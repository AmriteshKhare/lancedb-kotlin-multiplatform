use thiserror::Error;

#[derive(Debug, Error, uniffi::Error)]
pub enum LanceDbError {
    #[error("connection failed")]
    ConnectionFailed,
    #[error("table not found")]
    TableNotFound,
    #[error("search failed")]
    SearchFailed,
    #[error("seed failed")]
    SeedFailed,
    #[error("embedding failed")]
    EmbeddingFailed,
    #[error("invalid input")]
    InvalidInput,
    #[error("internal error")]
    Internal,
}

pub type LanceDbResult<T> = Result<T, LanceDbError>;
