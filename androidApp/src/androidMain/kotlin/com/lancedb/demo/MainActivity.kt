package com.lancedb.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lancedb.demo.ui.RecipeSearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    val viewModel: RecipeSearchViewModel = viewModel(
                        factory = RecipeSearchViewModel.factory(applicationContext),
                    )
                    RecipeSearchScreen(viewModel = viewModel)
                }
            }
        }
    }
}
