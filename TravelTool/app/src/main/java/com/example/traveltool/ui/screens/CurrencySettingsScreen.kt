package com.example.traveltool.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.data.CurrencyManager
import com.example.traveltool.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Manage currency list + exchange rates relative to the trip's base currency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySettingsScreen(
    baseCurrency: String,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currencies by remember { mutableStateOf(CurrencyManager.getCurrencyList(context)) }
    var eurRates by remember { mutableStateOf(CurrencyManager.loadCachedRates(context)) }
    var relativeRates by remember { mutableStateOf(CurrencyManager.getRatesRelativeTo(baseCurrency, eurRates)) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Add currency dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var newCode by remember { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Edit rate dialog state
    var editingCurrency by remember { mutableStateOf<String?>(null) }
    var editRateText by remember { mutableStateOf("") }

    fun refreshRelative() {
        eurRates = CurrencyManager.loadCachedRates(context)
        relativeRates = CurrencyManager.getRatesRelativeTo(baseCurrency, eurRates)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Currencies & Rates") },
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
            // ── Header ──────────────────────────────
            item {
                Text(
                    text = "Exchange Rates",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
                Text(
                    text = "Rates shown as: 1 [currency] = ? $baseCurrency\nTap a rate to edit it manually.",
                    fontSize = 12.sp,
                    color = colors.comment,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))

                // Refresh button
                Button(
                    onClick = {
                        isRefreshing = true
                        scope.launch {
                            CurrencyManager.fetchAndCacheRates(context)
                            refreshRelative()
                            currencies = CurrencyManager.getCurrencyList(context)
                            isRefreshing = false
                            Toast.makeText(context, "Rates updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.background,
                    ),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.background)
                    } else {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (isRefreshing) "Refreshing…" else "Refresh rates from web")
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Currency + Rate cards ────────────────
            val otherCurrencies = currencies.filter { it != baseCurrency }

            // Base currency card (always rate 1)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.2f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(baseCurrency, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        Spacer(Modifier.weight(1f))
                        Text("Base currency", fontSize = 13.sp, color = colors.comment)
                    }
                }
            }

            items(otherCurrencies) { code ->
                val rate = relativeRates[code]
                val isDefault = code in CurrencyManager.DEFAULT_CURRENCIES
                val rateDisplay = if (rate != null) {
                    val formatted = if (rate >= 100) String.format("%.2f", rate)
                    else if (rate >= 1) String.format("%.4f", rate)
                    else String.format("%.6f", rate)
                    "1 $code = $formatted $baseCurrency"
                } else {
                    "Rate not available"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.current),
                    onClick = {
                        editingCurrency = code
                        editRateText = if (rate != null) {
                            if (rate >= 100) String.format("%.2f", rate)
                            else if (rate >= 1) String.format("%.4f", rate)
                            else String.format("%.6f", rate)
                        } else ""
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(code, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.foreground)
                            Text(rateDisplay, fontSize = 13.sp, color = colors.green)
                        }
                        if (!isDefault) {
                            IconButton(
                                onClick = {
                                    CurrencyManager.removeCustomCurrency(context, code)
                                    currencies = CurrencyManager.getCurrencyList(context)
                                    refreshRelative()
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.Close, "Remove", tint = colors.red, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // ── Add Currency button ─────────────────
            item {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { newCode = ""; validationError = null; showAddDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Currency")
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    // ── Add Currency Dialog ─────────────────────────
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Currency") },
            text = {
                Column {
                    Text("Enter the 3-letter currency code (e.g. GBP, CHF, CZK, RON).", fontSize = 13.sp, color = colors.comment)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newCode,
                        onValueChange = { newCode = it.uppercase().take(3); validationError = null },
                        label = { Text("Currency code") },
                        placeholder = { Text("e.g. GBP") },
                        singleLine = true,
                        isError = validationError != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary, focusedLabelColor = colors.primary, cursorColor = colors.primary,
                        )
                    )
                    if (validationError != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(validationError!!, fontSize = 12.sp, color = colors.red)
                    }
                    if (isValidating) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Validating…", fontSize = 12.sp, color = colors.comment)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val code = newCode.uppercase().trim()
                        if (code.length != 3) { validationError = "Must be exactly 3 letters"; return@TextButton }
                        if (code in currencies) { validationError = "$code is already in the list"; return@TextButton }
                        isValidating = true; validationError = null
                        scope.launch {
                            val valid = CurrencyManager.validateCurrency(code)
                            isValidating = false
                            if (valid) {
                                CurrencyManager.addCustomCurrency(context, code)
                                CurrencyManager.fetchAndCacheRates(context)
                                currencies = CurrencyManager.getCurrencyList(context)
                                refreshRelative()
                                showAddDialog = false
                                Toast.makeText(context, "$code added", Toast.LENGTH_SHORT).show()
                            } else {
                                validationError = "Currency \"$code\" not found. Please check the code."
                            }
                        }
                    },
                    enabled = !isValidating,
                ) { Text("Add", color = colors.green) }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } },
            containerColor = colors.current, titleContentColor = colors.foreground, textContentColor = colors.foreground,
        )
    }

    // ── Edit Rate Dialog ────────────────────────────
    editingCurrency?.let { code ->
        AlertDialog(
            onDismissRequest = { editingCurrency = null },
            title = { Text("Edit Rate: $code") },
            text = {
                Column {
                    Text("How many $baseCurrency is 1 $code?", fontSize = 14.sp, color = colors.foreground)
                    Spacer(Modifier.height(4.dp))
                    Text("1 $code = ? $baseCurrency", fontSize = 12.sp, color = colors.comment)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editRateText,
                        onValueChange = { editRateText = it },
                        label = { Text("Rate in $baseCurrency") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary, focusedLabelColor = colors.primary, cursorColor = colors.primary,
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val rate = editRateText.toDoubleOrNull()
                        if (rate != null && rate > 0) {
                            CurrencyManager.saveManualRate(context, baseCurrency, code, rate)
                            refreshRelative()
                            editingCurrency = null
                            Toast.makeText(context, "Rate saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Save", color = colors.green) }
            },
            dismissButton = { TextButton(onClick = { editingCurrency = null }) { Text("Cancel") } },
            containerColor = colors.current, titleContentColor = colors.foreground, textContentColor = colors.foreground,
        )
    }
}
