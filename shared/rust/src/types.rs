pub const RECIPES_TABLE: &str = "recipes";
pub const VECTOR_DIM: usize = 384;

#[derive(Debug, Clone, uniffi::Record)]
pub struct RecipeResult {
    pub id: String,
    pub title: String,
    pub description: String,
    pub ingredients: String,
    pub score: f64,
    pub latency_ms: u64,
}

#[derive(Debug, Clone, serde::Deserialize, serde::Serialize)]
pub struct SeedRecipe {
    pub id: String,
    pub title: String,
    pub description: String,
    pub ingredients: String,
    pub vector: Vec<f32>,
}
