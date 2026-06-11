package com.lancedb.demo

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lancedb.kmp.ConnectionStatus
import com.lancedb.kmp.LanceDbRepository
import com.lancedb.kmp.SearchOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.lancedb_kmp.RecipeResult

class RecipeSearchViewModel(
    private val repository: LanceDbRepository,
    private val assetLoader: DemoAssetLoader,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _results = MutableStateFlow<List<RecipeResult>>(emptyList())
    val results: StateFlow<List<RecipeResult>> = _results.asStateFlow()

    private val _lastSearch = MutableStateFlow<SearchOutcome?>(null)
    val lastSearch: StateFlow<SearchOutcome?> = _lastSearch.asStateFlow()

    val connectionStatus = repository.connectionStatus
    val statusMessage = repository.statusMessage

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                assetLoader.prepareFiles()
                val seedJson = assetLoader.readSeedJson()
                repository.initializeAndSeed(seedJson)
            } catch (e: Exception) {
                // status flows updated in repository
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            if (repository.connectionStatus.value != ConnectionStatus.Ready) return@launch
            if (query.isBlank()) {
                _results.value = emptyList()
                _lastSearch.value = null
                return@launch
            }
            runSearch(query)
        }
    }

    fun searchNow() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            runSearch(_searchQuery.value)
        }
    }

    private suspend fun runSearch(query: String) {
        try {
            val outcome = repository.search(query)
            _lastSearch.value = outcome
            _results.value = outcome.results
        } catch (e: Exception) {
            _lastSearch.value = null
            _results.value = emptyList()
        }
    }

    fun openAddDialog() {
        _showAddDialog.value = true
    }

    fun dismissAddDialog() {
        _showAddDialog.value = false
    }

    fun addDocument(title: String, description: String, ingredients: String) {
        viewModelScope.launch {
            try {
                repository.addDocument(title, description, ingredients)
                dismissAddDialog()
                if (_searchQuery.value.isNotBlank()) {
                    runSearch(_searchQuery.value)
                }
            } catch (_: Exception) {
                dismissAddDialog()
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val loader = DemoAssetLoader(context)
                    val repo = LanceDbRepository(
                        dbPath = loader.databasePath(),
                        modelDir = loader.modelsPath(),
                    )
                    return RecipeSearchViewModel(repo, loader) as T
                }
            }
    }
}
