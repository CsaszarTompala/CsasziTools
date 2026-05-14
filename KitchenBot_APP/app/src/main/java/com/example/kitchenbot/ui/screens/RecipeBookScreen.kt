package com.example.kitchenbot.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kitchenbot.data.*
import com.example.kitchenbot.R
import com.example.kitchenbot.ui.theme.*
import com.example.kitchenbot.viewmodel.RecipeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBookScreen(
    viewModel: RecipeViewModel,
    onBack: () -> Unit,
    onAddToShoppingList: (List<Ingredient>) -> Unit
) {
    val currentRecipe = viewModel.currentRecipe

    BackHandler {
        if (viewModel.isAiPrompting || currentRecipe != null) viewModel.closeRecipe()
        else onBack()
    }

    when {
        viewModel.isAiPrompting -> {
            AiRecipePromptScreen(
                viewModel = viewModel,
                onBack = { viewModel.closeRecipe() }
            )
        }
        currentRecipe != null -> {
            RecipeDetailScreen(
                viewModel = viewModel,
                recipe = currentRecipe,
                onBack = { viewModel.closeRecipe() },
                onSave = { viewModel.saveRecipe(it) },
                onEdit = { viewModel.startEditing() },
                onToggleIngredient = { viewModel.toggleIngredientCheck(it) },
                onSelectAll = { viewModel.selectAllIngredients(it) },
                onAddToShoppingList = {
                    onAddToShoppingList(viewModel.getSelectedIngredients())
                },
                onDelete = {
                    viewModel.deleteRecipe(currentRecipe.id)
                }
            )
        }
        else -> {
            RecipeListScreen(
                viewModel = viewModel,
                onBack = onBack
            )
        }
    }
}

// ── AI Recipe Prompt Screen ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRecipePromptScreen(
    viewModel: RecipeViewModel,
    onBack: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipe_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.recipe_describe_hint)) },
                        shape = RoundedCornerShape(16.dp),
                        maxLines = 3,
                        enabled = !viewModel.isAiLoading
                    )
                    IconButton(
                        onClick = { viewModel.generateRecipeFromPrompt(prompt) },
                        enabled = prompt.isNotBlank() && !viewModel.isAiLoading
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            "Generate",
                            tint = if (prompt.isNotBlank() && !viewModel.isAiLoading)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (viewModel.isAiLoading) {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.recipe_ai_creating),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("🍳", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.recipe_describe_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.recipe_describe_example),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            viewModel.aiError?.let { error ->
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearAiError() }) {
                            Icon(Icons.Default.Close, "Dismiss")
                        }
                    }
                }
            }
        }
    }
}

// ── Recipe List Screen ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    viewModel: RecipeViewModel,
    onBack: () -> Unit
) {
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipe_book_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(Icons.Default.FilterList, "Filter")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.createNewRecipe() },
                icon = { Icon(Icons.Default.Add, "New Recipe") },
                text = { Text(stringResource(R.string.recipe_new)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text(stringResource(R.string.recipe_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (showFilters) {
                FilterSection(viewModel)
            }

            val recipes = viewModel.filteredRecipes()
            if (recipes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📖", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.recipe_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.recipe_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recipes) { recipe ->
                        RecipeCard(
                            recipe = recipe,
                            onClick = { viewModel.openRecipe(recipe) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilterSection(viewModel: RecipeViewModel) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.recipe_price_label), style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = viewModel.filterPrice == null,
                    onClick = { viewModel.updateFilterPrice(null) },
                    label = { Text(stringResource(R.string.recipe_filter_all)) }
                )
            }
            items(PriceRange.entries) { price ->
                FilterChip(
                    selected = viewModel.filterPrice == price,
                    onClick = { viewModel.updateFilterPrice(if (viewModel.filterPrice == price) null else price) },
                    label = { Text(price.symbol) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val tags = viewModel.allTags()
        if (tags.isNotEmpty()) {
            Text(stringResource(R.string.recipe_tags_label), style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = viewModel.filterTag == null,
                        onClick = { viewModel.updateFilterTag(null) },
                        label = { Text(stringResource(R.string.recipe_filter_all)) }
                    )
                }
                items(tags) { tag ->
                    FilterChip(
                        selected = viewModel.filterTag == tag,
                        onClick = { viewModel.updateFilterTag(if (viewModel.filterTag == tag) null else tag) },
                        label = { Text(tag) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recipe_show_archived), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = viewModel.showArchived,
                onCheckedChange = { viewModel.updateShowArchived(it) }
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
    }
}

@Composable
fun RecipeCard(recipe: Recipe, onClick: () -> Unit) {
    val priceColor = when (recipe.priceRange) {
        PriceRange.CHEAP -> CheapGreen
        PriceRange.MEDIUM -> MediumOrange
        PriceRange.EXPENSIVE -> ExpensiveRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recipe.name.ifBlank { stringResource(R.string.recipe_unnamed) },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = recipe.priceRange.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    color = priceColor
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                recipe.tags.take(3).forEach { tag ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${recipe.ingredients.size} ingredients · ${recipe.steps.size} steps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            if (recipe.isArchived) {
                Text(
                    stringResource(R.string.recipe_archived_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// ── Recipe Detail Screen ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    viewModel: RecipeViewModel,
    recipe: Recipe,
    onBack: () -> Unit,
    onSave: (Recipe) -> Unit,
    onEdit: () -> Unit,
    onToggleIngredient: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onAddToShoppingList: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var chatPrompt by remember { mutableStateOf("") }
    var editName by remember(recipe.id) { mutableStateOf(recipe.name) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.recipe_delete_title)) },
            text = { Text(stringResource(R.string.recipe_delete_body)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text(stringResource(R.string.recipe_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            viewModel.isEditing -> stringResource(R.string.recipe_edit_title)
                            viewModel.showDiff -> stringResource(R.string.recipe_diff_title)
                            else -> recipe.name.ifBlank { stringResource(R.string.recipe_fallback_title) }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!viewModel.isEditing && !viewModel.showDiff) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(12.dp)
                ) {
                    viewModel.aiError?.let { error ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.clearAiError() }) {
                                    Icon(Icons.Default.Close, "Dismiss", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    when {
                        viewModel.showDiff -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.rejectDiff() },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.recipe_undo_changes)) }
                                Button(
                                    onClick = { viewModel.acceptDiff() },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.recipe_keep_changes)) }
                            }
                        }
                        viewModel.isEditing -> {
                            if (viewModel.isAiLoading) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.recipe_ai_modifying), style = MaterialTheme.typography.bodyMedium)
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = chatPrompt,
                                        onValueChange = { chatPrompt = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text(stringResource(R.string.recipe_ai_modify_hint)) },
                                        shape = RoundedCornerShape(16.dp),
                                        maxLines = 2,
                                        singleLine = false
                                    )
                                    IconButton(
                                        onClick = {
                                            viewModel.modifyRecipeWithAi(chatPrompt)
                                            chatPrompt = ""
                                        },
                                        enabled = chatPrompt.isNotBlank()
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            "Send",
                                            tint = if (chatPrompt.isNotBlank())
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onBack,
                                        modifier = Modifier.weight(1f)
                                    ) { Text(stringResource(R.string.cancel)) }
                                    Button(
                                        onClick = { onSave(recipe.copy(name = editName)) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(stringResource(R.string.recipe_save)) }
                                }
                            }
                        }
                        else -> {
                            if (recipe.name.isBlank()) {
                                OutlinedTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    placeholder = { Text(stringResource(R.string.recipe_name_hint)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Button(
                                    onClick = {
                                        if (editName.isNotBlank()) {
                                            onSave(recipe.copy(name = editName))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    enabled = editName.isNotBlank()
                                ) { Text(stringResource(R.string.recipe_save_btn)) }
                            }
                            // Save button for unsaved recipes (generated but not yet persisted)
                            val isSaved = viewModel.recipes.any { it.id == recipe.id }
                            if (recipe.name.isNotBlank() && !isSaved) {
                                Button(
                                    onClick = { onSave(recipe) },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) { Text(stringResource(R.string.recipe_save_btn)) }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onEdit,
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(R.string.recipe_modify_btn)) }
                                if (recipe.name.isNotBlank()) {
                                    Button(
                                        onClick = { onAddToShoppingList() },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(stringResource(R.string.recipe_to_shopping)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (viewModel.showDiff && viewModel.previousRecipe != null) {
                item {
                    DiffSection(
                        title = "Ingredients",
                        oldItems = viewModel.previousRecipe!!.ingredients.map { formatIngredient(it) },
                        newItems = recipe.ingredients.map { formatIngredient(it) }
                    )
                }
                item {
                    DiffSection(
                        title = "Steps",
                        oldItems = viewModel.previousRecipe!!.steps,
                        newItems = recipe.steps
                    )
                }
            } else {
                // Normal view
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.recipe_ingredients_title), style = MaterialTheme.typography.titleLarge)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.recipe_select_all), style = MaterialTheme.typography.labelMedium)
                                    Checkbox(
                                        checked = recipe.ingredients.all { it.checked },
                                        onCheckedChange = { onSelectAll(it) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                itemsIndexed(recipe.ingredients) { index, ingredient ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = ingredient.checked,
                            onCheckedChange = { onToggleIngredient(index) }
                        )
                        Text(
                            text = formatIngredient(ingredient),
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (ingredient.checked) TextDecoration.LineThrough else null,
                            color = if (ingredient.checked) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.recipe_steps_title), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                itemsIndexed(recipe.steps) { index, step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(32.dp)
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                if (recipe.tags.isNotEmpty() || recipe.allergens.isNotEmpty()) {
                    item { Spacer(Modifier.height(16.dp)) }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (recipe.tags.isNotEmpty()) {
                                    Text(stringResource(R.string.recipe_tags_title), style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(4.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(recipe.tags) { tag ->
                                            SuggestionChip(onClick = {}, label = { Text(tag) })
                                        }
                                    }
                                }
                                if (recipe.allergens.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(stringResource(R.string.recipe_allergens_title), style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(4.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(recipe.allergens) { allergen ->
                                            SuggestionChip(
                                                onClick = {},
                                                label = { Text(allergen) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ── Diff View ────────────────────────────────────────────────────────────────

@Composable
fun DiffSection(
    title: String,
    oldItems: List<String>,
    newItems: List<String>
) {
    val removed = oldItems - newItems.toSet()
    val added = newItems - oldItems.toSet()
    val unchanged = oldItems.intersect(newItems.toSet())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            removed.forEach { line ->
                Text(
                    text = "− $line",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33FF0000), RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(2.dp))
            }

            added.forEach { line ->
                Text(
                    text = "+ $line",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x3300CC00), RoundedCornerShape(4.dp))
                        .padding(8.dp),
                    color = Color(0xFF006600)
                )
                Spacer(Modifier.height(2.dp))
            }

            unchanged.forEach { line ->
                Text(
                    text = "  $line",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

private fun formatIngredient(ingredient: Ingredient): String = buildString {
    if (ingredient.amount.isNotBlank()) append("${ingredient.amount} ")
    if (ingredient.unit.isNotBlank()) append("${ingredient.unit} ")
    append(ingredient.name)
}
