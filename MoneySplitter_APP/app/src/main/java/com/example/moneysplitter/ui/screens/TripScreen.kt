package com.example.moneysplitter.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.moneysplitter.viewmodel.TripViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    viewModel: TripViewModel,
    fileName: String,
    onBack: () -> Unit
) {
    val trip by viewModel.trip.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showTripInfoSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(fileName) {
        viewModel.loadTrip(fileName)
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val tabs = listOf(
        TabItem("Expenses", Icons.Default.Receipt),
        TabItem("People", Icons.Default.Group),
        TabItem("Currencies", Icons.Default.CurrencyExchange),
        TabItem("Results", Icons.Default.BarChart),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(trip.name, maxLines = 1)
                        val dateInfo = buildTripDateSubtitle(trip.startDate, trip.endDate)
                        if (dateInfo != null) {
                            Text(
                                dateInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showTripInfoSheet = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit trip info")
                    }
                    IconButton(onClick = {
                        try {
                            val file = viewModel.generatePdf(context)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Share Trip Report")
                            )
                        } catch (_: Exception) {
                            // Error generating PDF
                        }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> ExpensesTab(viewModel)
                1 -> PeopleTab(viewModel)
                2 -> CurrenciesTab(viewModel)
                3 -> ResultsTab(viewModel)
            }
        }
    }

    // Trip info bottom sheet
    if (showTripInfoSheet) {
        TripInfoSheet(
            tripName = trip.name,
            startDate = trip.startDate,
            endDate = trip.endDate,
            onDismiss = { showTripInfoSheet = false },
            onSave = { name, start, end ->
                viewModel.updateTripInfo(name, start, end)
                showTripInfoSheet = false
            }
        )
    }
}

private fun buildTripDateSubtitle(startDate: String?, endDate: String?): String? {
    if (startDate == null && endDate == null) return null
    val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val displayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val startStr = startDate?.let {
        try { displayFormat.format(isoFormat.parse(it)!!) } catch (_: Exception) { it }
    } ?: "—"
    val endStr = endDate?.let {
        try { displayFormat.format(isoFormat.parse(it)!!) } catch (_: Exception) { it }
    } ?: "—"
    return "$startStr  →  $endStr"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripInfoSheet(
    tripName: String,
    startDate: String?,
    endDate: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, startDate: String?, endDate: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isoFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    var name by remember { mutableStateOf(tripName) }
    var start by remember { mutableStateOf(startDate) }
    var end by remember { mutableStateOf(endDate) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

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
            Text("Trip Details", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Trip Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Start date
            OutlinedTextField(
                value = start?.let {
                    try { displayFormat.format(isoFormat.parse(it)!!) } catch (_: Exception) { it }
                } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Start Date (optional)") },
                placeholder = { Text("Tap to pick") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showStartPicker = true },
                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                trailingIcon = {
                    if (start != null) {
                        IconButton(onClick = { start = null }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                enabled = false
            )

            // End date
            OutlinedTextField(
                value = end?.let {
                    try { displayFormat.format(isoFormat.parse(it)!!) } catch (_: Exception) { it }
                } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("End Date (optional)") },
                placeholder = { Text("Tap to pick") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEndPicker = true },
                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                trailingIcon = {
                    if (end != null) {
                        IconButton(onClick = { end = null }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                enabled = false
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = { onSave(name, start, end) },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = start?.let {
                try { isoFormat.parse(it)?.time } catch (_: Exception) { null }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { start = isoFormat.format(Date(it)) }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = end?.let {
                try { isoFormat.parse(it)?.time } catch (_: Exception) { null }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { end = isoFormat.format(Date(it)) }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }
}

private data class TabItem(
    val title: String,
    val icon: ImageVector
)
