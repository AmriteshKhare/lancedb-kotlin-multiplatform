mod database;
mod embedder;
mod error;
mod runtime;
mod types;

use std::sync::Arc;

pub use database::LanceDbClient;
pub use error::LanceDbError;
pub use types::RecipeResult;

#[uniffi::export]
pub fn create_lance_db_client(
    db_path: String,
    model_dir: String,
) -> Result<Arc<LanceDbClient>, LanceDbError> {
    LanceDbClient::new(db_path, model_dir)
}

uniffi::setup_scaffolding!();
