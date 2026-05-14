package com.example.kitchenbot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kitchenbot.data.ApiKeyStore
import com.example.kitchenbot.ui.screens.*
import com.example.kitchenbot.ui.theme.KitchenBotTheme
import com.example.kitchenbot.viewmodel.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KitchenBotTheme {
                KitchenBotApp()
            }
        }
    }
}

sealed class AppScreen {
    data object Home : AppScreen()
    data object RecipeBook : AppScreen()
    data object ShoppingList : AppScreen()
    data object HomeInventory : AppScreen()
    data object CookFromHome : AppScreen()
    data object Settings : AppScreen()
}

@Composable
fun KitchenBotApp() {
    val context = LocalContext.current
    var hasApiKey by remember { mutableStateOf(ApiKeyStore.hasApiKey(context)) }
    var showApiKeyDialog by remember { mutableStateOf(!hasApiKey) }

    if (showApiKeyDialog && !hasApiKey) {
        ApiKeyGateDialog(
            onApiKeySaved = {
                hasApiKey = true
                showApiKeyDialog = false
            }
        )
        return
    }

    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

    val recipeViewModel: RecipeViewModel = viewModel()
    val shoppingViewModel: ShoppingViewModel = viewModel()
    val homeInventoryViewModel: HomeInventoryViewModel = viewModel()
    val cookViewModel: CookViewModel = viewModel()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (currentScreen) {
            is AppScreen.Home -> HomeScreen(
                onNavigate = { currentScreen = it }
            )
            is AppScreen.RecipeBook -> RecipeBookScreen(
                viewModel = recipeViewModel,
                onBack = { currentScreen = AppScreen.Home },
                onAddToShoppingList = { ingredients ->
                    shoppingViewModel.addItemsFromRecipe(ingredients)
                }
            )
            is AppScreen.ShoppingList -> ShoppingListScreen(
                viewModel = shoppingViewModel,
                onBack = { currentScreen = AppScreen.Home }
            )
            is AppScreen.HomeInventory -> HomeInventoryScreen(
                viewModel = homeInventoryViewModel,
                onBack = { currentScreen = AppScreen.Home }
            )
            is AppScreen.CookFromHome -> CookFromHomeScreen(
                viewModel = cookViewModel,
                recipeViewModel = recipeViewModel,
                homeInventoryViewModel = homeInventoryViewModel,
                onBack = { currentScreen = AppScreen.Home },
                onOpenRecipe = { currentScreen = AppScreen.RecipeBook }
            )
            is AppScreen.Settings -> SettingsScreen(
                onBack = { currentScreen = AppScreen.Home }
            )
        }
    }
}

@Composable
fun ApiKeyGateDialog(onApiKeySaved: () -> Unit) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        stringResource(R.string.gate_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        stringResource(R.string.gate_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            showError = false
                        },
                        label = { Text(stringResource(R.string.gate_api_key_label)) },
                        placeholder = { Text(stringResource(R.string.gate_api_key_placeholder)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (showError) {
                        Text(
                            stringResource(R.string.gate_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = {
                            val key = apiKey.trim()
                            if (key.isBlank() || !key.startsWith("sk-")) {
                                showError = true
                            } else {
                                ApiKeyStore.setApiKey(context, key)
                                onApiKeySaved()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.gate_continue))
                    }
                }
            }
        }
    }
}
