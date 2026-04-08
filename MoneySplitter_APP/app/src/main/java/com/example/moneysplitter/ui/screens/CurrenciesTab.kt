package com.example.moneysplitter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.moneysplitter.viewmodel.TripViewModel

@Composable
fun CurrenciesTab(viewModel: TripViewModel) {
    val trip by viewModel.trip.collectAsState()
    val isFetching by viewModel.isFetchingRates.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var currencyToDelete by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Base currency selector
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Base Currency",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    CurrencyDropdown(
                        selected = trip.baseCurrency,
                        options = trip.currencies,
                        onSelect = { viewModel.changeBaseCurrency(it) }
                    )
                    Text(
                        "All conversion rates are relative to the base currency",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Fetch rates button
        item {
            Button(
                onClick = { viewModel.fetchLiveRates() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isFetching
            ) {
                if (isFetching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fetching rates...")
                } else {
                    Icon(Icons.Default.Public, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fetch Live Rates")
                }
            }
        }

        // Currency list header
        item {
            Text(
                "Currencies",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(trip.currencies, key = { it }) { currency ->
            CurrencyCard(
                currency = currency,
                rate = trip.conversionRates[currency],
                isBase = currency == trip.baseCurrency,
                baseCurrency = trip.baseCurrency,
                onRateChange = { newRate ->
                    viewModel.updateConversionRate(currency, newRate)
                },
                onDelete = {
                    if (currency != trip.baseCurrency) {
                        currencyToDelete = currency
                    }
                }
            )
        }

        // Add currency button
        item {
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Currency")
            }
        }
    }

    if (showAddDialog) {
        AddCurrencyDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { code, rate ->
                viewModel.addCurrency(code, rate)
                showAddDialog = false
            }
        )
    }

    currencyToDelete?.let { currency ->
        AlertDialog(
            onDismissRequest = { currencyToDelete = null },
            title = { Text("Remove Currency") },
            text = {
                Text("Remove $currency? Expenses in this currency will be converted to ${trip.baseCurrency}.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeCurrency(currency)
                        currencyToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { currencyToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CurrencyDropdown(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { expanded = true }) {
        Text(selected)
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        options.forEach { currency ->
            DropdownMenuItem(
                text = { Text(currency) },
                onClick = {
                    onSelect(currency)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun CurrencyCard(
    currency: String,
    rate: Double?,
    isBase: Boolean,
    baseCurrency: String,
    onRateChange: (Double) -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = currency,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isBase) {
                        AssistChip(
                            onClick = {},
                            label = { Text("BASE", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                if (!isBase && rate != null) {
                    var rateText by remember(rate) { mutableStateOf("%.4f".format(rate)) }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { newText ->
                            rateText = newText
                            newText.toDoubleOrNull()?.let { onRateChange(it) }
                        },
                        label = { Text("1 $currency = ? $baseCurrency") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (!isBase) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCurrencyDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Double) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf("1.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Currency") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(3) },
                    label = { Text("Currency code") },
                    placeholder = { Text("e.g. GBP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Rate (vs base currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val rate = rateText.toDoubleOrNull() ?: 1.0
                    onAdd(code, rate)
                },
                enabled = code.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
