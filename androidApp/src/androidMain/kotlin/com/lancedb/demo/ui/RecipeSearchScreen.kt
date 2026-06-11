package com.lancedb.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lancedb.demo.RecipeSearchViewModel
import com.lancedb.kmp.ConnectionStatus
import uniffi.lancedb_kmp.RecipeResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeSearchScreen(viewModel: RecipeSearchViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.results.collectAsState()
    val lastSearch by viewModel.lastSearch.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()

    var addTitle by remember { mutableStateOf("") }
    var addDescription by remember { mutableStateOf("") }
    var addIngredients by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Offline Recipe Search")
                        Text(
                            "Powered by LanceDB + Rust",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (connectionStatus == ConnectionStatus.Ready) {
                FloatingActionButton(onClick = { viewModel.openAddDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = "Add document")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            ConnectionChip(connectionStatus, statusMessage)

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Semantic search") },
                placeholder = { Text("e.g. spicy breakfast with eggs") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                enabled = connectionStatus == ConnectionStatus.Ready,
            )

            Spacer(modifier = Modifier.height(8.dp))

            lastSearch?.let { search ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "Found ${search.results.size} results in ${search.latencyMs}ms",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (connectionStatus == ConnectionStatus.Connecting ||
                connectionStatus == ConnectionStatus.Seeding
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                items(results, key = { it.id }) { recipe ->
                    RecipeCard(recipe)
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAddDialog() },
            title = { Text("Add recipe") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = addTitle,
                        onValueChange = { addTitle = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = addDescription,
                        onValueChange = { addDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = addIngredients,
                        onValueChange = { addIngredients = it },
                        label = { Text("Ingredients") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addDocument(addTitle, addDescription, addIngredients)
                        addTitle = ""
                        addDescription = ""
                        addIngredients = ""
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAddDialog() }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ConnectionChip(status: ConnectionStatus, message: String) {
    val label = when (status) {
        ConnectionStatus.Disconnected -> "Disconnected"
        ConnectionStatus.Connecting -> "Connecting…"
        ConnectionStatus.Seeding -> "Seeding…"
        ConnectionStatus.Ready -> "Ready"
        ConnectionStatus.Error -> "Error"
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = when (status) {
            ConnectionStatus.Ready -> MaterialTheme.colorScheme.secondaryContainer
            ConnectionStatus.Error -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            if (message.isNotBlank()) {
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(recipe.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(recipe.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Ingredients: ${recipe.ingredients}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Score ${"%.3f".format(recipe.score)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
