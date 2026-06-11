use std::sync::Arc;

use arrow_array::types::Float32Type;
use arrow_array::Array;
use arrow_array::cast::AsArray;
use arrow_array::{FixedSizeListArray, RecordBatch, StringArray};
use arrow_schema::{DataType, Field, Schema};
use futures::TryStreamExt;
use lancedb::connection::Connection;
use lancedb::index::Index;
use lancedb::query::{ExecutableQuery, QueryBase, Select};

use crate::embedder::Embedder;
use crate::error::{LanceDbError, LanceDbResult};
use crate::runtime::block_on;
use crate::types::{RecipeResult, SeedRecipe, RECIPES_TABLE, VECTOR_DIM};

#[derive(uniffi::Object)]
pub struct LanceDbClient {
    connection: Connection,
    embedder: Embedder,
}

impl LanceDbClient {
    pub fn new(db_path: String, model_dir: String) -> Result<Arc<Self>, LanceDbError> {
        let embedder = Embedder::from_model_dir(&model_dir)?;
        let connection = block_on(async {
            lancedb::connect(&db_path)
                .execute()
                .await
                .map_err(|_| LanceDbError::ConnectionFailed)
        })?;

        Ok(Arc::new(Self {
            connection,
            embedder,
        }))
    }
}

#[uniffi::export]
impl LanceDbClient {
    pub fn list_tables(&self) -> Result<Vec<String>, LanceDbError> {
        block_on(async {
            self.connection
                .table_names()
                .execute()
                .await
                .map_err(|_| LanceDbError::Internal)
        })
    }

    pub fn is_seeded(&self) -> Result<bool, LanceDbError> {
        let tables = self.list_tables()?;
        if !tables.iter().any(|name| name == RECIPES_TABLE) {
            return Ok(false);
        }

        let count = block_on(async {
            let table = self
                .connection
                .open_table(RECIPES_TABLE)
                .execute()
                .await
                .map_err(|_| LanceDbError::Internal)?;

            table
                .count_rows(None)
                .await
                .map_err(|_| LanceDbError::Internal)
        })?;

        Ok(count > 0)
    }

    pub fn seed_from_json(&self, json: String) -> Result<(), LanceDbError> {
        let recipes: Vec<SeedRecipe> =
            serde_json::from_str(&json).map_err(|_| LanceDbError::SeedFailed)?;

        if recipes.is_empty() {
            return Err(LanceDbError::SeedFailed);
        }

        let batch = recipes_to_batch(&recipes)?;

        block_on(async {
            if self
                .connection
                .table_names()
                .execute()
                .await
                .map_err(|_| LanceDbError::SeedFailed)?
                .iter()
                .any(|name| name == RECIPES_TABLE)
            {
                self.connection
                    .drop_table(RECIPES_TABLE, &[])
                    .await
                    .map_err(|_| LanceDbError::SeedFailed)?;
            }

            let table = self
                .connection
                .create_table(RECIPES_TABLE, batch)
                .execute()
                .await
                .map_err(|_| LanceDbError::SeedFailed)?;

            let _ = table
                .create_index(&["vector"], Index::Auto)
                .execute()
                .await;

            Ok(())
        })
    }

    pub fn search(&self, query: String, limit: u32) -> Result<Vec<RecipeResult>, LanceDbError> {
        if query.trim().is_empty() {
            return Ok(Vec::new());
        }

        let started = std::time::Instant::now();
        let query_vector = self.embedder.embed_query(query.trim())?;

        let batches = block_on(async {
            let table = self
                .connection
                .open_table(RECIPES_TABLE)
                .execute()
                .await
                .map_err(|_| LanceDbError::TableNotFound)?;

            table
                .query()
                .nearest_to(query_vector.as_slice())
                .map_err(|_| LanceDbError::SearchFailed)?
                .limit(limit as usize)
                .select(Select::Columns(vec![
                    "id".into(),
                    "title".into(),
                    "description".into(),
                    "ingredients".into(),
                    "_distance".into(),
                ]))
                .execute()
                .await
                .map_err(|_| LanceDbError::SearchFailed)?
                .try_collect::<Vec<_>>()
                .await
                .map_err(|_| LanceDbError::SearchFailed)
        })?;

        let latency_ms = started.elapsed().as_millis() as u64;
        parse_search_results(batches, latency_ms)
    }

    pub fn add_document(
        &self,
        title: String,
        description: String,
        ingredients: String,
    ) -> Result<RecipeResult, LanceDbError> {
        if title.trim().is_empty() {
            return Err(LanceDbError::InvalidInput);
        }

        let id = format!("user-{}", uuid_simple());
        let vector = self
            .embedder
            .embed_document(title.trim(), description.trim(), ingredients.trim())?;

        let recipe = SeedRecipe {
            id: id.clone(),
            title: title.trim().to_string(),
            description: description.trim().to_string(),
            ingredients: ingredients.trim().to_string(),
            vector,
        };

        let batch = recipes_to_batch(&[recipe])?;

        block_on(async {
            let table = self
                .connection
                .open_table(RECIPES_TABLE)
                .execute()
                .await
                .map_err(|_| LanceDbError::TableNotFound)?;

            table
                .add(batch)
                .execute()
                .await
                .map_err(|_| LanceDbError::Internal)
        })?;

        Ok(RecipeResult {
            id,
            title: title.trim().to_string(),
            description: description.trim().to_string(),
            ingredients: ingredients.trim().to_string(),
            score: 1.0,
            latency_ms: 0,
        })
    }
}

fn recipes_to_batch(recipes: &[SeedRecipe]) -> LanceDbResult<RecordBatch> {
    for recipe in recipes {
        if recipe.vector.len() != VECTOR_DIM {
            return Err(LanceDbError::SeedFailed);
        }
    }

    let ids = StringArray::from(recipes.iter().map(|r| r.id.as_str()).collect::<Vec<_>>());
    let titles = StringArray::from(recipes.iter().map(|r| r.title.as_str()).collect::<Vec<_>>());
    let descriptions =
        StringArray::from(recipes.iter().map(|r| r.description.as_str()).collect::<Vec<_>>());
    let ingredients =
        StringArray::from(recipes.iter().map(|r| r.ingredients.as_str()).collect::<Vec<_>>());

    let list_values: Vec<Option<Vec<Option<f32>>>> = recipes
        .iter()
        .map(|recipe| Some(recipe.vector.iter().map(|v| Some(*v)).collect()))
        .collect();
    let vectors = FixedSizeListArray::from_iter_primitive::<Float32Type, _, _>(
        list_values,
        VECTOR_DIM as i32,
    );

    let schema = Arc::new(Schema::new(vec![
        Field::new("id", DataType::Utf8, false),
        Field::new("title", DataType::Utf8, false),
        Field::new("description", DataType::Utf8, false),
        Field::new("ingredients", DataType::Utf8, false),
        Field::new("vector", vectors.data_type().clone(), false),
    ]));

    RecordBatch::try_new(
        schema,
        vec![
            Arc::new(ids),
            Arc::new(titles),
            Arc::new(descriptions),
            Arc::new(ingredients),
            Arc::new(vectors),
        ],
    )
    .map_err(|_| LanceDbError::SeedFailed)
}

fn parse_search_results(
    batches: Vec<RecordBatch>,
    latency_ms: u64,
) -> LanceDbResult<Vec<RecipeResult>> {
    let mut results = Vec::new();

    for batch in batches {
        let ids = batch
            .column_by_name("id")
            .ok_or(LanceDbError::SearchFailed)?;
        let titles = batch
            .column_by_name("title")
            .ok_or(LanceDbError::SearchFailed)?;
        let descriptions = batch
            .column_by_name("description")
            .ok_or(LanceDbError::SearchFailed)?;
        let ingredients = batch
            .column_by_name("ingredients")
            .ok_or(LanceDbError::SearchFailed)?;
        let distances = batch
            .column_by_name("_distance")
            .ok_or(LanceDbError::SearchFailed)?;

        let ids = ids.as_string::<i32>();
        let titles = titles.as_string::<i32>();
        let descriptions = descriptions.as_string::<i32>();
        let ingredients = ingredients.as_string::<i32>();
        let distances = distances.as_primitive::<arrow_array::types::Float32Type>();

        for row in 0..batch.num_rows() {
            let distance = distances.value(row) as f64;
            let score = 1.0 / (1.0 + distance);
            results.push(RecipeResult {
                id: ids.value(row).to_string(),
                title: titles.value(row).to_string(),
                description: descriptions.value(row).to_string(),
                ingredients: ingredients.value(row).to_string(),
                score,
                latency_ms,
            });
        }
    }

    Ok(results)
}

fn uuid_simple() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    format!("{nanos:x}")
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::types::SeedRecipe;
    use tempfile::tempdir;

    fn zero_vector() -> Vec<f32> {
        vec![0.0; VECTOR_DIM]
    }

    #[test]
    fn connect_and_list_tables() {
        let dir = tempdir().unwrap();
        let db_path = dir.path().join("lancedb");
        let client = LanceDbClient::new(
            db_path.to_string_lossy().to_string(),
            "/tmp/nonexistent-model".to_string(),
        )
        .expect("client");

        let tables = client.list_tables().expect("tables");
        assert!(tables.is_empty());
    }

    #[test]
    fn seed_and_search_roundtrip() {
        let dir = tempdir().unwrap();
        let db_path = dir.path().join("lancedb");
        let client = LanceDbClient::new(
            db_path.to_string_lossy().to_string(),
            "/tmp/nonexistent-model".to_string(),
        )
        .expect("client");

        let seed = vec![SeedRecipe {
            id: "1".into(),
            title: "Spicy Shakshuka".into(),
            description: "Eggs poached in spicy tomato sauce".into(),
            ingredients: "eggs, tomatoes, chili, cumin".into(),
            vector: {
                let mut v = zero_vector();
                v[0] = 1.0;
                v
            },
        }];

        client
            .seed_from_json(serde_json::to_string(&seed).unwrap())
            .expect("seed");

        assert!(client.is_seeded().expect("seeded"));

        let mut query = zero_vector();
        query[0] = 1.0;
        let results = block_on(async {
            let table = client
                .connection
                .open_table(RECIPES_TABLE)
                .execute()
                .await
                .unwrap();
            table
                .query()
                .nearest_to(query.as_slice())
                .unwrap()
                .limit(1)
                .execute()
                .await
                .unwrap()
                .try_collect::<Vec<_>>()
                .await
                .unwrap()
        });

        let parsed = parse_search_results(results, 1).expect("parsed");
        assert_eq!(parsed.len(), 1);
        assert_eq!(parsed[0].title, "Spicy Shakshuka");
    }
}
