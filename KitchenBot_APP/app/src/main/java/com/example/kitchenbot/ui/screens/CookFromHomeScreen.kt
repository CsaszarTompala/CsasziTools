package com.example.kitchenbot.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kitchenbot.R
import com.example.kitchenbot.data.HomeItem
import com.example.kitchenbot.data.Recipe
import com.example.kitchenbot.viewmodel.CookViewModel
import com.example.kitchenbot.viewmodel.HomeInventoryViewModel
import com.example.kitchenbot.viewmodel.RecipeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookFromHomeScreen(
    viewModel: CookViewModel,
    recipeViewModel: RecipeViewModel,
    homeInventoryViewModel: HomeInventoryViewModel,
    onBack: () -> Unit,
    onOpenRecipe: () -> Unit
) {
    var showSuggestions by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cook_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // What's at home summary
            item {
                val homeItems = viewModel.getHomeItems()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.cook_items_at_home, homeItems.size),
                            style = MaterialTheme.typography.titleMedium
                        )
                        val oldItems = homeInventoryViewModel.getOldItems()
                        if (oldItems.isNotEmpty()) {
                            Text(
                                stringResource(R.string.cook_old_items_warning, oldItems.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Extra ingredients slider
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.cook_extra_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when {
                                viewModel.extraSlider == 0f -> stringResource(R.string.cook_extra_none)
                                viewModel.extraSlider < 0.3f -> stringResource(R.string.cook_extra_few)
                                viewModel.extraSlider < 0.7f -> stringResource(R.string.cook_extra_some)
                                else -> stringResource(R.string.cook_extra_lots)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Slider(
                            value = viewModel.extraSlider,
                            onValueChange = { viewModel.updateExtraSlider(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0%", style = MaterialTheme.typography.labelMedium)
                            Text("100%", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Prioritize old items
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.cook_prioritize_fridge), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.cook_prioritize_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = viewModel.prioritizeOld,
                            onCheckedChange = { viewModel.updatePrioritizeOld(it) }
                        )
                    }
                }
            }

            // Vegetarian toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.cook_vegetarian), style = MaterialTheme.typography.titleMedium)
                        }
                        Switch(
                            checked = viewModel.isVegetarian,
                            onCheckedChange = { viewModel.updateVegetarian(it) }
                        )
                    }
                }
            }

            // Meat type selector (if not vegetarian)
            if (!viewModel.isVegetarian) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.cook_meat_type), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = viewModel.selectedMeatType == null,
                                        onClick = { viewModel.updateMeatType(null) },
                                        label = { Text(stringResource(R.string.cook_any)) }
                                    )
                                }
                                items(viewModel.getAvailableMeats()) { meat ->
                                    FilterChip(
                                        selected = viewModel.selectedMeatType == meat,
                                        onClick = { viewModel.updateMeatType(meat) },
                                        label = { Text(meat) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Allergen exclusions
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.cook_exclude_allergens), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val allergens = listOf(
                            "Gluten" to R.string.cook_allergen_gluten,
                            "Dairy" to R.string.cook_allergen_dairy,
                            "Nuts" to R.string.cook_allergen_nuts,
                            "Eggs" to R.string.cook_allergen_eggs,
                            "Soy" to R.string.cook_allergen_soy,
                            "Shellfish" to R.string.cook_allergen_shellfish
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(allergens) { (key, labelRes) ->
                                FilterChip(
                                    selected = key in viewModel.excludeAllergens,
                                    onClick = { viewModel.toggleAllergen(key) },
                                    label = { Text(stringResource(labelRes)) }
                                )
                            }
                        }
                    }
                }
            }

            // Dish type selector
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.cook_dish_type), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = viewModel.selectedDishType == null,
                                    onClick = { viewModel.updateDishType(null) },
                                    label = { Text(stringResource(R.string.cook_any)) }
                                )
                            }
                            items(viewModel.getDishTypes()) { dish ->
                                FilterChip(
                                    selected = viewModel.selectedDishType == dish,
                                    onClick = { viewModel.updateDishType(dish) },
                                    label = { Text(dish) }
                                )
                            }
                        }
                    }
                }
            }

            // Generate button
            item {
                Button(
                    onClick = {
                        viewModel.generateSuggestions()
                        showSuggestions = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(16.dp),
                    enabled = !viewModel.isLoading
                ) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cook_ai_thinking), style = MaterialTheme.typography.titleMedium)
                    } else {
                        Icon(Icons.Default.AutoAwesome, "Generate", modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cook_generate), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // AI Error
            viewModel.aiError?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearError() }) {
                                Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Suggestions
            if (showSuggestions && viewModel.suggestions.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.cook_suggestions_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(viewModel.suggestions) { suggestion ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Use AI to generate full recipe from suggestion
                                viewModel.generateRecipeFromSuggestion(
                                    title = suggestion,
                                    onResult = { recipe ->
                                        val finalRecipe = recipe.copy(
                                            isTemporary = true,
                                            archivedAt = System.currentTimeMillis() + 10L * 24 * 60 * 60 * 1000
                                        )
                                        recipeViewModel.saveRecipe(finalRecipe)
                                        recipeViewModel.openRecipe(finalRecipe)
                                        onOpenRecipe()
                                    },
                                    onError = { /* error is shown via viewModel.aiError */ }
                                )
                            },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🍳", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                "Open",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Some bottom spacing
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
