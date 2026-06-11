use std::fs;
use std::path::Path;
use std::sync::Mutex;

use fastembed::{
    InitOptions, InitOptionsUserDefined, Pooling, TextEmbedding, TokenizerFiles,
    UserDefinedEmbeddingModel,
};

use crate::error::{LanceDbError, LanceDbResult};
use crate::types::VECTOR_DIM;

pub struct Embedder {
    inner: Mutex<TextEmbedding>,
}

impl Embedder {
    pub fn from_model_dir(model_dir: &str) -> LanceDbResult<Self> {
        let model_path = Path::new(model_dir).join("model.onnx");
        let tokenizer_path = Path::new(model_dir).join("tokenizer.json");

        let inner = if model_path.exists() && tokenizer_path.exists() {
            let onnx_file = fs::read(&model_path).map_err(|_| LanceDbError::EmbeddingFailed)?;
            let tokenizer_files = TokenizerFiles {
                tokenizer_file: fs::read(&tokenizer_path)
                    .map_err(|_| LanceDbError::EmbeddingFailed)?,
                config_file: read_optional(Path::new(model_dir).join("config.json")),
                special_tokens_map_file: read_optional(
                    Path::new(model_dir).join("special_tokens_map.json"),
                ),
                tokenizer_config_file: read_optional(
                    Path::new(model_dir).join("tokenizer_config.json"),
                ),
            };

            let user_model =
                UserDefinedEmbeddingModel::new(onnx_file, tokenizer_files).with_pooling(Pooling::Mean);

            TextEmbedding::try_new_from_user_defined(user_model, InitOptionsUserDefined::default())
                .map_err(|_| LanceDbError::EmbeddingFailed)?
        } else {
            TextEmbedding::try_new(
                InitOptions::new(fastembed::EmbeddingModel::AllMiniLML6V2)
                    .with_show_download_progress(false),
            )
            .map_err(|_| LanceDbError::EmbeddingFailed)?
        };

        Ok(Self {
            inner: Mutex::new(inner),
        })
    }

    pub fn embed_query(&self, query: &str) -> LanceDbResult<Vec<f32>> {
        let prefixed = format!("query: {query}");
        let mut model = self
            .inner
            .lock()
            .map_err(|_| LanceDbError::Internal)?;

        let embeddings = model
            .embed(vec![prefixed], None)
            .map_err(|_| LanceDbError::EmbeddingFailed)?;

        let vector = embeddings
            .into_iter()
            .next()
            .ok_or(LanceDbError::EmbeddingFailed)?;

        if vector.len() != VECTOR_DIM {
            return Err(LanceDbError::EmbeddingFailed);
        }

        Ok(vector)
    }

    pub fn embed_document(
        &self,
        title: &str,
        description: &str,
        ingredients: &str,
    ) -> LanceDbResult<Vec<f32>> {
        let text = format!("passage: {title}. {description}. Ingredients: {ingredients}");
        let mut model = self
            .inner
            .lock()
            .map_err(|_| LanceDbError::Internal)?;

        let embeddings = model
            .embed(vec![text], None)
            .map_err(|_| LanceDbError::EmbeddingFailed)?;

        embeddings
            .into_iter()
            .next()
            .ok_or(LanceDbError::EmbeddingFailed)
    }
}

fn read_optional(path: impl AsRef<Path>) -> Vec<u8> {
    fs::read(path).unwrap_or_default()
}
