package com.example.kitchenbot.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kitchenbot.R
import com.example.kitchenbot.data.*
import com.example.kitchenbot.ui.theme.*
import com.example.kitchenbot.viewmodel.HomeInventoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeInventoryScreen(
    viewModel: HomeInventoryViewModel,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showPendingSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Reload data every time this screen is composed (e.g. after purchasing in shopping list)
    LaunchedEffect(Unit) {
        viewModel.reload()
    }

    // Show pending sheet whenever pending purchases appear
    LaunchedEffect(viewModel.hasPending) {
        if (viewModel.hasPending) {
            showPendingSheet = true
        }
    }

    BackHandler(onBack = onBack)

    // Pending purchases sheet
    if (showPendingSheet && viewModel.pendingPurchases.isNotEmpty()) {
        PendingPurchasesSheet(
            pending = viewModel.pendingPurchases,
            viewModel = viewModel,
            onDismiss = { showPendingSheet = false },
            onConfirm = { resolved ->
                viewModel.processPendingPurchases(resolved)
                showPendingSheet = false
            }
        )
    }

    if (showAddDialog) {
        AddHomeItemDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onAdd = { name, amount, dim, cat ->
                viewModel.addItem(name, amount, dim, cat)
                showAddDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (viewModel.hasPending) {
                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                            IconButton(onClick = { showPendingSheet = true }) {
                                Icon(Icons.Default.Notifications, "Pending")
                            }
                        }
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, "Add") },
                text = { Text(stringResource(R.string.inventory_add_item)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        val sorted = viewModel.sortedItems().let { items ->
            if (searchQuery.isBlank()) items
            else items.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.inventory_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

        if (sorted.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (searchQuery.isBlank()) {
                        Text("🏠", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.inventory_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.inventory_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Text("🔍", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.inventory_search_empty), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                            HomeItemRow(
                                item = item,
                                onToggleOld = { viewModel.toggleOld(item.id) },
                                onDelete = { viewModel.removeItem(item.id) },
                                onEditAmount = { amt, dim -> viewModel.updateAmount(item.id, amt, dim) }
                            )
                        }
                    }
                } else {
                    items(sorted) { item ->
                        HomeItemRow(
                            item = item,
                            onToggleOld = { viewModel.toggleOld(item.id) },
                            onDelete = { viewModel.removeItem(item.id) },
                            onEditAmount = { amt, dim -> viewModel.updateAmount(item.id, amt, dim) }
                        )
                    }
                }
            }
        }
        } // Column
    }
}

@Composable
fun HomeItemRow(
    item: HomeItem,
    onToggleOld: () -> Unit,
    onDelete: () -> Unit,
    onEditAmount: (Double, String) -> Unit
) {
    var showEditAmount by remember { mutableStateOf(false) }
    var editAmountText by remember { mutableStateOf(item.amount.toString()) }
    var editDimension by remember { mutableStateOf(item.dimension) }

    val bgColor = if (item.isOld)
        OldItemAmber.copy(alpha = 0.3f)
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.category.emoji} ${item.name}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${item.amount} ${item.dimension}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (item.isOld) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.inventory_old_badge)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                }
                IconButton(onClick = onToggleOld) {
                    Icon(
                        if (item.isOld) Icons.Default.Refresh else Icons.Default.Warning,
                        if (item.isOld) "Mark Fresh" else "Mark Old",
                        tint = if (item.isOld) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = { showEditAmount = true }) {
                    Icon(Icons.Default.Edit, "Edit Amount", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(18.dp))
                }
            }

            if (showEditAmount) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = editAmountText,
                        onValueChange = { editAmountText = it },
                        label = { Text(stringResource(R.string.amount_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = editDimension,
                        onValueChange = { editDimension = it },
                        label = { Text(stringResource(R.string.unit_label)) },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    IconButton(onClick = {
                        val amt = editAmountText.toDoubleOrNull() ?: 0.0
                        onEditAmount(amt, editDimension)
                        showEditAmount = false
                    }) {
                        Icon(Icons.Default.Check, "Save", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun AddHomeItemDialog(
    viewModel: HomeInventoryViewModel,
    onDismiss: () -> Unit,
    onAdd: (String, Double, String, ProductCategory) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("1") }
    var dimension by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ProductCategory.OTHER) }

    // Auto-fill dimension from memory when name changes, AI fallback for new items
    LaunchedEffect(name) {
        if (name.length > 2) {
            val dim = viewModel.getDimensionForItem(name)
            if (dim != null) {
                dimension = dim
            } else {
                viewModel.guessItemDetails(name) { aiDim, aiCat ->
                    if (dimension.isBlank()) dimension = aiDim
                    category = aiCat
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inventory_add_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.inventory_item_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text(stringResource(R.string.amount_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dimension,
                        onValueChange = { dimension = it },
                        label = { Text(stringResource(R.string.unit_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(stringResource(R.string.category_label), style = MaterialTheme.typography.labelLarge)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ProductCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text("${cat.emoji}") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    onAdd(name, amt, dimension, category)
                },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingPurchasesSheet(
    pending: List<PendingPurchase>,
    viewModel: HomeInventoryViewModel,
    onDismiss: () -> Unit,
    onConfirm: (List<HomeItem>) -> Unit
) {
    // Track amounts for each pending item
    val amountStates = remember {
        pending.map { p ->
            mutableStateOf(p.suggestedAmount?.toString() ?: "1")
        }
    }
    val dimensionStates = remember {
        pending.map { p ->
            val dim = viewModel.getDimensionForItem(p.name) ?: p.dimension
            mutableStateOf(dim.ifBlank { "pieces" })
        }
    }
    // Track which items to skip (not add to home inventory)
    val skipStates = remember {
        pending.map { mutableStateOf(false) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inventory_pending_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(pending.indices.toList()) { idx ->
                    val p = pending[idx]
                    val isSkipped = skipStates[idx].value
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isSkipped) Modifier.alpha(0.4f) else Modifier),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${p.category.emoji} ${p.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { skipStates[idx].value = !isSkipped }
                                ) {
                                    Text(
                                        if (isSkipped) "↩" else stringResource(R.string.inventory_skip_item),
                                        color = if (isSkipped) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            if (!isSkipped) {
                                Spacer(Modifier.height(8.dp))

                                // Quick amount buttons
                                val offers = viewModel.getAmountOffers(p.name, dimensionStates[idx].value)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(offers) { offer ->
                                        val label = if (offer == offer.toLong().toDouble())
                                            "${offer.toLong()}" else "$offer"
                                        OutlinedButton(
                                            onClick = { amountStates[idx].value = label },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("$label ${dimensionStates[idx].value}")
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = amountStates[idx].value,
                                        onValueChange = { amountStates[idx].value = it },
                                        label = { Text(stringResource(R.string.custom_label)) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = dimensionStates[idx].value,
                                        onValueChange = { dimensionStates[idx].value = it },
                                        label = { Text(stringResource(R.string.unit_label)) },
                                        singleLine = true,
                                        modifier = Modifier.width(80.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val resolved = pending.mapIndexedNotNull { idx, p ->
                    if (skipStates[idx].value) return@mapIndexedNotNull null
                    val amt = amountStates[idx].value.toDoubleOrNull() ?: 1.0
                    val dim = dimensionStates[idx].value
                    // Track custom amount if round
                    viewModel.addCustomAmountOffer(p.name, amt)
                    HomeItem(
                        name = p.name,
                        amount = amt,
                        dimension = dim,
                        category = p.category
                    )
                }
                onConfirm(resolved)
            }) { Text(stringResource(R.string.inventory_proceed)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.later)) }
        }
    )
}
