package com.example.moneysplitter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.moneysplitter.data.Expense
import com.example.moneysplitter.ui.components.AddExpenseSheet
import com.example.moneysplitter.ui.components.ScanReceiptSheet
import com.example.moneysplitter.viewmodel.TripViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ExpensesTab(viewModel: TripViewModel) {
    val trip by viewModel.trip.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var showScanSheet by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (trip.expenses.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No expenses yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (trip.people.isEmpty())
                        "Add some people first, then start logging expenses"
                    else
                        "Tap + to add your first expense",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    SummaryCard(
                        expenseCount = trip.expenses.size,
                        peopleCount = trip.people.size
                    )
                }
                items(trip.expenses, key = { it.id }) { expense ->
                    ExpenseCard(
                        expense = expense,
                        allPeople = trip.people,
                        onEdit = { editingExpense = expense },
                        onDelete = { viewModel.removeExpense(expense.id) },
                        onToggleSettled = { viewModel.toggleExpenseSettled(expense.id) }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            SmallFloatingActionButton(
                onClick = {
                    if (trip.people.isNotEmpty()) {
                        showScanSheet = true
                    }
                },
                containerColor = if (trip.people.isNotEmpty())
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Scan receipt",
                    tint = if (trip.people.isNotEmpty())
                        MaterialTheme.colorScheme.onTertiaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FloatingActionButton(
                onClick = {
                    if (trip.people.isNotEmpty()) {
                        showAddSheet = true
                    }
                },
                containerColor = if (trip.people.isNotEmpty())
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add expense",
                    tint = if (trip.people.isNotEmpty())
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showAddSheet) {
        AddExpenseSheet(
            people = trip.people,
            currencies = trip.currencies,
            defaultCurrency = trip.baseCurrency,
            onDismiss = { showAddSheet = false },
            onSave = { expense ->
                viewModel.addExpense(expense)
                showAddSheet = false
            }
        )
    }

    if (showScanSheet) {
        ScanReceiptSheet(
            people = trip.people,
            currencies = trip.currencies,
            defaultCurrency = trip.baseCurrency,
            onDismiss = { showScanSheet = false },
            onItemsAdded = { expenses ->
                expenses.forEach { viewModel.addExpense(it) }
                showScanSheet = false
            }
        )
    }

    editingExpense?.let { expense ->
        AddExpenseSheet(
            people = trip.people,
            currencies = trip.currencies,
            defaultCurrency = trip.baseCurrency,
            existingExpense = expense,
            onDismiss = { editingExpense = null },
            onSave = { updated ->
                viewModel.updateExpense(updated)
                editingExpense = null
            }
        )
    }
}

@Composable
private fun SummaryCard(expenseCount: Int, peopleCount: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$expenseCount",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "expenses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$peopleCount",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "people",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ExpenseCard(
    expense: Expense,
    allPeople: List<String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleSettled: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isSettled = expense.settled
    val cardAlpha = if (isSettled) 0.65f else 1f

    ElevatedCard(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Settled badge
                    if (isSettled) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "SETTLED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Name
                    val displayName = expense.displayName
                    if (displayName.isNotBlank()) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (isSettled) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }

                    // Amount
                    Text(
                        text = formatAmount(expense.amount, expense.currency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isSettled)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.primary,
                        textDecoration = if (isSettled) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isSettled) "Unmark settled" else "Mark as settled") },
                            onClick = {
                                showMenu = false
                                onToggleSettled()
                            },
                            leadingIcon = {
                                Icon(
                                    if (isSettled) Icons.Default.RadioButtonUnchecked
                                    else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            // Notes
            val notes = expense.notes?.takeIf { it.isNotBlank() }
            if (notes != null) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Date
            val dateText = expense.date?.let { dateStr ->
                try {
                    val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val displayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    displayFormat.format(isoFormat.parse(dateStr)!!)
                } catch (_: Exception) { dateStr }
            }
            if (dateText != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Paid by ${expense.paidBy}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val splitText = when {
                expense.splitAmong.isEmpty() || expense.splitAmong.size == allPeople.size ->
                    "Split among everyone"
                expense.splitAmong.size == 1 ->
                    "Only ${expense.splitAmong.first()}"
                else ->
                    "Split: ${expense.splitAmong.joinToString(", ")}"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = splitText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatAmount(amount: Double, currency: String): String {
    return if (amount == amount.toLong().toDouble()) {
        "%,.0f %s".format(amount, currency)
    } else {
        "%,.2f %s".format(amount, currency)
    }
}
