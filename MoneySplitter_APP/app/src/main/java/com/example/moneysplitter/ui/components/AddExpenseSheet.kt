package com.example.moneysplitter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.moneysplitter.data.Expense
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseSheet(
    people: List<String>,
    currencies: List<String>,
    defaultCurrency: String,
    existingExpense: Expense? = null,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var description by remember {
        mutableStateOf(existingExpense?.description ?: "")
    }
    var amountText by remember {
        mutableStateOf(
            if (existingExpense != null && existingExpense.amount > 0)
                existingExpense.amount.let {
                    if (it == it.toLong().toDouble()) "%.0f".format(it) else "%.2f".format(it)
                }
            else ""
        )
    }
    var selectedCurrency by remember {
        mutableStateOf(existingExpense?.currency ?: defaultCurrency)
    }
    var selectedPayer by remember {
        mutableStateOf(existingExpense?.paidBy ?: "")
    }
    var selectedSplit by remember {
        mutableStateOf(existingExpense?.splitAmong?.toSet() ?: people.toSet())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title
            Text(
                text = if (existingExpense != null) "Edit Expense" else "Add Expense",
                style = MaterialTheme.typography.headlineSmall
            )

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                placeholder = { Text("e.g. Dinner, Taxi, Groceries") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }
            )

            // Amount
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount") },
                placeholder = { Text("0.00") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) }
            )

            // Currency selector
            Column {
                Text(
                    "Currency",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currencies.forEach { currency ->
                        FilterChip(
                            selected = selectedCurrency == currency,
                            onClick = { selectedCurrency = currency },
                            label = { Text(currency) }
                        )
                    }
                }
            }

            // Who paid
            Column {
                Text(
                    "Who paid?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    people.forEach { person ->
                        FilterChip(
                            selected = selectedPayer == person,
                            onClick = { selectedPayer = person },
                            label = { Text(person) },
                            leadingIcon = if (selectedPayer == person) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            // Split among
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Split among",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { selectedSplit = people.toSet() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("All", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(
                            onClick = { selectedSplit = emptySet() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("None", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    people.forEach { person ->
                        FilterChip(
                            selected = person in selectedSplit,
                            onClick = {
                                selectedSplit = if (person in selectedSplit)
                                    selectedSplit - person
                                else
                                    selectedSplit + person
                            },
                            label = { Text(person) },
                            leadingIcon = if (person in selectedSplit) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (amount > 0 && selectedPayer.isNotBlank()) {
                            onSave(
                                Expense(
                                    id = existingExpense?.id ?: UUID.randomUUID().toString(),
                                    amount = amount,
                                    currency = selectedCurrency,
                                    paidBy = selectedPayer,
                                    splitAmong = selectedSplit.toList(),
                                    description = description.trim()
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0 && selectedPayer.isNotBlank()
                ) {
                    Text(if (existingExpense != null) "Update" else "Add")
                }
            }
        }
    }
}
