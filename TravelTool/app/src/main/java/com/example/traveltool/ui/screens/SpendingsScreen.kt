package com.example.traveltool.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.data.*
import com.example.traveltool.ui.components.CurrencyPicker
import com.example.traveltool.ui.theme.*

/**
 * Manage daily and other spendings for a trip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingsScreen(
    tripId: String,
    tripViewModel: TripViewModel,
    onBack: () -> Unit
) {
    val trip = tripViewModel.getTripById(tripId)

    if (trip == null) {
        onBack()
        return
    }

    val context = LocalContext.current
    val currencies = remember { mutableStateOf(CurrencyManager.getCurrencyList(context)) }

    // Daily spending state
    var showAddDailyDialog by remember { mutableStateOf(false) }
    var editingDaily by remember { mutableStateOf<DailySpending?>(null) }
    var dailyToDelete by remember { mutableStateOf<DailySpending?>(null) }

    // Other spending state
    var showAddOtherDialog by remember { mutableStateOf(false) }
    var editingOther by remember { mutableStateOf<OtherSpending?>(null) }
    var otherToDelete by remember { mutableStateOf<OtherSpending?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spendings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Daily Spendings ────────────────────
            item {
                Text(
                    text = "Daily Spendings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DraculaPurple,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
                Text(
                    text = "Spending per day (e.g. food, activities)",
                    fontSize = 12.sp,
                    color = DraculaComment,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            if (trip.dailySpendings.isEmpty()) {
                item {
                    Text("No daily spendings yet.", fontSize = 14.sp, color = DraculaComment,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
            }

            items(trip.dailySpendings, key = { it.id }) { spending ->
                SpendingCard(
                    name = spending.name,
                    amount = spending.amountPerDay,
                    amountLabel = "per day",
                    currency = spending.currency,
                    onClick = { editingDaily = spending },
                    onDelete = { dailyToDelete = spending }
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showAddDailyDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Daily Spending")
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Other Spendings ────────────────────
            item {
                HorizontalDivider(color = DraculaCurrent)
                Text(
                    text = "Other Spendings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DraculaPurple,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
                Text(
                    text = "One-time or total expenses",
                    fontSize = 12.sp,
                    color = DraculaComment,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            if (trip.otherSpendings.isEmpty()) {
                item {
                    Text("No other spendings yet.", fontSize = 14.sp, color = DraculaComment,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
            }

            items(trip.otherSpendings, key = { it.id }) { spending ->
                SpendingCard(
                    name = spending.name,
                    amount = spending.amount,
                    amountLabel = "total",
                    currency = spending.currency,
                    onClick = { editingOther = spending },
                    onDelete = { otherToDelete = spending }
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showAddOtherDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Other Spending")
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    // ── Add Daily Spending Dialog ──────────────
    if (showAddDailyDialog) {
        SpendingEditDialog("Add Daily Spending", "", "", currencies.value, trip.displayCurrency,
            { n, a, c ->
                tripViewModel.updateTrip(trip.copy(dailySpendings = trip.dailySpendings + DailySpending(name = n, amountPerDay = a, currency = c)))
                showAddDailyDialog = false
            },
            { showAddDailyDialog = false })
    }

    editingDaily?.let { spending ->
        SpendingEditDialog("Edit Daily Spending", spending.name, if (spending.amountPerDay > 0) spending.amountPerDay.toString() else "", currencies.value, spending.currency,
            { n, a, c ->
                tripViewModel.updateTrip(trip.copy(dailySpendings = trip.dailySpendings.map { if (it.id == spending.id) it.copy(name = n, amountPerDay = a, currency = c) else it }))
                editingDaily = null
            },
            { editingDaily = null })
    }

    dailyToDelete?.let { spending ->
        DeleteConfirmDialog("Delete Daily Spending", spending.name,
            { tripViewModel.updateTrip(trip.copy(dailySpendings = trip.dailySpendings.filter { it.id != spending.id })); dailyToDelete = null },
            { dailyToDelete = null })
    }

    // ── Add Other Spending Dialog ──────────────
    if (showAddOtherDialog) {
        SpendingEditDialog("Add Other Spending", "", "", currencies.value, trip.displayCurrency,
            { n, a, c ->
                tripViewModel.updateTrip(trip.copy(otherSpendings = trip.otherSpendings + OtherSpending(name = n, amount = a, currency = c)))
                showAddOtherDialog = false
            },
            { showAddOtherDialog = false })
    }

    editingOther?.let { spending ->
        SpendingEditDialog("Edit Other Spending", spending.name, if (spending.amount > 0) spending.amount.toString() else "", currencies.value, spending.currency,
            { n, a, c ->
                tripViewModel.updateTrip(trip.copy(otherSpendings = trip.otherSpendings.map { if (it.id == spending.id) it.copy(name = n, amount = a, currency = c) else it }))
                editingOther = null
            },
            { editingOther = null })
    }

    otherToDelete?.let { spending ->
        DeleteConfirmDialog("Delete Other Spending", spending.name,
            { tripViewModel.updateTrip(trip.copy(otherSpendings = trip.otherSpendings.filter { it.id != spending.id })); otherToDelete = null },
            { otherToDelete = null })
    }
}

@Composable
private fun SpendingCard(
    name: String,
    amount: Double,
    amountLabel: String,
    currency: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = DraculaCurrent),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "(No name)" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DraculaForeground)
                Text("💰 $amount $currency ($amountLabel)", fontSize = 13.sp, color = DraculaGreen)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Delete", tint = DraculaRed, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpendingEditDialog(
    title: String,
    initialName: String,
    initialAmount: String,
    currencies: List<String>,
    initialCurrency: String,
    onConfirm: (name: String, amount: Double, currency: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var amountText by remember { mutableStateOf(initialAmount) }
    var currency by remember { mutableStateOf(initialCurrency) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Food, Activities") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DraculaPurple, focusedLabelColor = DraculaPurple, cursorColor = DraculaPurple,
                    )
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DraculaPurple, focusedLabelColor = DraculaPurple, cursorColor = DraculaPurple,
                        )
                    )
                    CurrencyPicker(selected = currency, currencies = currencies, onSelect = { currency = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), amountText.toDoubleOrNull() ?: 0.0, currency)
                    }
                }
            ) { Text("OK", color = DraculaGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = DraculaCurrent, titleContentColor = DraculaForeground, textContentColor = DraculaForeground,
    )
}

@Composable
private fun DeleteConfirmDialog(
    title: String,
    itemName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text("Delete \"${itemName.ifBlank { "(unnamed)" }}\"?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = DraculaRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = DraculaCurrent, titleContentColor = DraculaForeground, textContentColor = DraculaForeground,
    )
}

