package com.lancedb.kmp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uniffi.lancedb_kmp.LanceDbClient
import uniffi.lancedb_kmp.RecipeResult
import uniffi.lancedb_kmp.createLanceDbClient

enum class ConnectionStatus {
    Disconnected,
    Connecting,
    Seeding,
    Ready,
    Error,
}

data class SearchOutcome(
    val results: List<RecipeResult>,
    val latencyMs: Long,
    val query: String,
)

class LanceDbRepository(
    private val dbPath: String,
    private val modelDir: String,
) {
    private var client: LanceDbClient? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    suspend fun initializeAndSeed(seedJson: String) = withContext(Dispatchers.Default) {
        _connectionStatus.value = ConnectionStatus.Connecting
        _statusMessage.value = "Opening LanceDB…"
        try {
            client = createLanceDbClient(dbPath, modelDir)
            val db = requireClient()
            if (!db.isSeeded()) {
                _connectionStatus.value = ConnectionStatus.Seeding
                _statusMessage.value = "Seeding recipes and building vector index…"
                db.seedFromJson(seedJson)
            }
            _connectionStatus.value = ConnectionStatus.Ready
            _statusMessage.value = "Seeded ${db.listTables().size} table(s) — offline semantic search enabled"
        } catch (e: Exception) {
            _connectionStatus.value = ConnectionStatus.Error
            _statusMessage.value = e.message ?: "Failed to initialize database"
            throw e
        }
    }

    suspend fun search(query: String, limit: UInt = 8u): SearchOutcome = withContext(Dispatchers.Default) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@withContext SearchOutcome(emptyList(), 0L, trimmed)
        }
        val started = System.currentTimeMillis()
        val results = requireClient().search(trimmed, limit)
        val elapsed = System.currentTimeMillis() - started
        val rustLatency = results.firstOrNull()?.latencyMs?.toLong() ?: elapsed
        SearchOutcome(
            results = results,
            latencyMs = maxOf(elapsed, rustLatency),
            query = trimmed,
        )
    }

    suspend fun addDocument(
        title: String,
        description: String,
        ingredients: String,
    ): RecipeResult = withContext(Dispatchers.Default) {
        requireClient().addDocument(title, description, ingredients)
    }

    private fun requireClient(): LanceDbClient =
        client ?: error("LanceDB client is not initialized")
}
