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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding
import com.example.kitchenbot.R
import com.example.kitchenbot.data.ProductCategory
import com.example.kitchenbot.viewmodel.ShoppingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    viewModel: ShoppingViewModel,
    onBack: () -> Unit
) {
    var newItemText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ProductCategory.OTHER) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showRemindMe by remember { mutableStateOf(false) }
    var showStoreSelector by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    if (showRemindMe) {
        RemindMeDialog(
            suggestions = viewModel.getRemindMeSuggestions(),
            onAdd = { name ->
                viewModel.addItem(name)
            },
            onDismiss = { showRemindMe = false }
        )
    }

    if (showStoreSelector) {
        StoreSelectorDialog(
            layouts = viewModel.storeLayouts,
            selected = viewModel.selectedStore,
            onSelect = { viewModel.updateSelectedStore(it); showStoreSelector = false },
            onDismiss = { showStoreSelector = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shopping_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showStoreSelector = true }) {
                        Icon(Icons.Default.Store, "Store Layout")
                    }
                    IconButton(onClick = { viewModel.toggleSortMode() }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
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
                Column(modifier = Modifier.navigationBarsPadding().padding(12.dp)) {
                    // Add item row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Category selector button
                        IconButton(onClick = { showCategoryPicker = !showCategoryPicker }) {
                            Text(selectedCategory.emoji)
                        }
                        OutlinedTextField(
                            value = newItemText,
                            onValueChange = { newItemText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.shopping_add_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        IconButton(
                            onClick = {
                                viewModel.addItem(newItemText, selectedCategory)
                                newItemText = ""
                            }
                        ) {
                            Icon(Icons.Default.Add, "Add", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Category picker
                    if (showCategoryPicker) {
                        LazyRow(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(ProductCategory.entries) { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = {
                                        selectedCategory = cat
                                        showCategoryPicker = false
                                    },
                                    label = { Text("${cat.emoji} ${cat.label}") }
                                )
                            }
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showRemindMe = true },
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.shopping_remind_me)) }
                        Button(
                            onClick = { viewModel.purchaseChecked() },
                            modifier = Modifier.weight(1f),
                            enabled = viewModel.items.any { it.checked }
                        ) { Text(stringResource(R.string.shopping_purchased)) }
                    }
                }
            }
        }
    ) { padding ->
        val sorted = viewModel.sortedItems()

        if (sorted.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛒", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.shopping_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.shopping_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Group by category if sorted by category
                if (viewModel.sortByCategory) {
                    val grouped = sorted.groupBy { it.category }
                    grouped.forEach { (category, items) ->
                        item {
                            Text(
                                text = "${category.emoji} ${category.label}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(items) { item ->
                            ShoppingItemRow(
                                item = item,
                                onToggle = { viewModel.toggleCheck(item.id) },
                                onDelete = { viewModel.removeItem(item.id) }
                            )
                        }
                    }
                } else {
                    items(sorted) { item ->
                        ShoppingItemRow(
                            item = item,
                            onToggle = { viewModel.toggleCheck(item.id) },
                            onDelete = { viewModel.removeItem(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShoppingItemRow(
    item: com.example.kitchenbot.data.ShoppingItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.checked)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = item.checked, onCheckedChange = { onToggle() })
            Text(
                text = buildString {
                    append("${item.category.emoji} ${item.name}")
                    if (item.recipeAmount.isNotBlank() || item.recipeUnit.isNotBlank()) {
                        append(" (${item.recipeAmount}${item.recipeUnit})")
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Close,
                    "Remove",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun RemindMeDialog(
    suggestions: List<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_remind_me)) },
        text = {
            if (suggestions.isEmpty()) {
                Text(stringResource(R.string.shopping_no_suggestions))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(suggestions) { name ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(name) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, modifier = Modifier.weight(1f))
                                Icon(
                                    Icons.Default.Add,
                                    "Add",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        }
    )
}

@Composable
fun StoreSelectorDialog(
    layouts: List<com.example.kitchenbot.data.StoreLayout>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shopping_store_sort)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(layouts) { layout ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(layout.name) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (layout.name == selected)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(layout.name, modifier = Modifier.weight(1f))
                            if (layout.name == selected) {
                                Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
