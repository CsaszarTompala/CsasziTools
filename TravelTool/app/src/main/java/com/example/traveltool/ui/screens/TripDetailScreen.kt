package com.example.traveltool.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.traveltool.data.CurrencyManager
import com.example.traveltool.data.Trip
import com.example.traveltool.data.TripViewModel
import com.example.traveltool.ui.theme.*
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Calculate total trip cost in the trip's display currency.
 * Includes: accommodations, tolls, tickets, fees, and spendings.
 */
private fun calculateTotalTripCost(trip: Trip, eurRates: Map<String, Double>, tripDays: Int): Double {
    var total = 0.0

    // Accommodations
    trip.accommodations.forEach { accom ->
        if (accom.pricePerNight != null && accom.pricePerNight > 0) {
            val nights = ((accom.endMillis - accom.startMillis) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(1)
            val cost = accom.pricePerNight * nights
            total += CurrencyManager.convert(cost, accom.priceCurrency, trip.displayCurrency, eurRates)
        }
    }

    // Toll roads
    trip.tollRoads.forEach { toll ->
        total += CurrencyManager.convert(toll.price, toll.currency, trip.displayCurrency, eurRates)
    }

    // Plane tickets
    trip.planeTickets.forEach { ticket ->
        total += CurrencyManager.convert(ticket.price, ticket.currency, trip.displayCurrency, eurRates)
    }

    // Additional fees
    trip.additionalFees.forEach { fee ->
        total += CurrencyManager.convert(fee.price, fee.currency, trip.displayCurrency, eurRates)
    }

    // Daily spendings
    trip.dailySpendings.forEach { spending ->
        val totalDailySpending = spending.amountPerDay * tripDays
        total += CurrencyManager.convert(totalDailySpending, spending.currency, trip.displayCurrency, eurRates)
    }

    // Other spendings
    trip.otherSpendings.forEach { spending ->
        total += CurrencyManager.convert(spending.amount, spending.currency, trip.displayCurrency, eurRates)
    }

    // Fuel cost (if estimated distance + consumption + price are all available)
    if (trip.estimatedDrivingDistanceKm != null && trip.fuelConsumption != null && trip.fuelPricePerLiter != null) {
        val litres = (trip.estimatedDrivingDistanceKm / 100.0) * trip.fuelConsumption
        val fuelCost = litres * trip.fuelPricePerLiter
        total += CurrencyManager.convert(fuelCost, trip.fuelPriceCurrency, trip.displayCurrency, eurRates)
    }

    return total
}

/**
 * Trip detail / settings page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: String,
    tripViewModel: TripViewModel,
    onBack: () -> Unit,
    onAccommodation: (String) -> Unit,
    onTravelSettings: (String) -> Unit,
    onDailyActivities: (String) -> Unit,
    onCurrencySettings: (String) -> Unit,
    onSpendings: (String) -> Unit
) {
    val trip = tripViewModel.getTripById(tripId)

    if (trip == null) {
        onBack()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(trip.name) }

    var newLocation by remember { mutableStateOf(trip.location) }

    var showDatesDialog by remember { mutableStateOf(false) }

    var showStartingPointDialog by remember { mutableStateOf(false) }
    var newStartingPoint by remember { mutableStateOf(trip.startingPoint) }

    var showCurrencyDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val startDate  = remember(trip.startMillis) { dateFormat.format(Date(trip.startMillis)) }
    val endDate    = remember(trip.endMillis)   { dateFormat.format(Date(trip.endMillis)) }

    // Calculate total trip cost
    val eurRates = remember(trip) { CurrencyManager.loadCachedRates(context) }
    val tripDays = remember(trip) { ((trip.endMillis - trip.startMillis) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(1) }
    val totalCost = remember(trip, eurRates, tripDays) {
        calculateTotalTripCost(trip, eurRates, tripDays)
    }

    // Location permission launcher for "Detect my location"
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            detectCurrentLocation(context, scope) { detected ->
                newStartingPoint = detected
                tripViewModel.updateTrip(trip.copy(startingPoint = detected))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            newName = trip.name
                            showRenameDialog = true
                        }
                    ) {
                        Text(trip.name)
                        if (trip.hasWarning) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Trip has warnings",
                                tint = DraculaYellow,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Trip info header (tappable to edit location & dates) ---
            Column(
                modifier = Modifier
                    .clickable {
                        newLocation = trip.location
                        showDatesDialog = true
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "$startDate – $endDate",
                    fontSize = 14.sp,
                    color = DraculaOrange,
                )
                if (trip.location.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "📍 ${trip.location}",
                        fontSize = 14.sp,
                        color = DraculaComment,
                    )
                }
            }

            HorizontalDivider(color = DraculaCurrent)

            // --- Total Cost Summary ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DraculaPurple.copy(alpha = 0.15f)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Trip Cost", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DraculaPurple)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "💰 %.2f %s".format(totalCost, trip.displayCurrency),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DraculaGreen,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Based on accommodations, tolls, tickets, fees, fuel & spendings", fontSize = 11.sp, color = DraculaComment)
                }
            }

            HorizontalDivider(color = DraculaCurrent)

            // --- Starting Point ---
            SettingsRow(
                title = "Starting Point",
                subtitle = trip.startingPoint.ifBlank { "Not set" },
                onClick = {
                    newStartingPoint = trip.startingPoint
                    showStartingPointDialog = true
                }
            )

            HorizontalDivider(color = DraculaCurrent)

            // --- Accommodation ---
            SettingsRow(
                title = "Accommodation",
                subtitle = "${trip.accommodations.size} accommodation(s)",
                showWarning = trip.accommodations.isEmpty() || trip.accommodations.any { it.hasWarning },
                onClick = { onAccommodation(tripId) }
            )

            HorizontalDivider(color = DraculaCurrent)

            // --- Travel Mode ---
            SettingsRow(
                title = "Travel Mode",
                subtitle = when (trip.travelMode) {
                    com.example.traveltool.data.TravelMode.CAR -> "Car" + if (trip.fuelConsumption != null) " • ${trip.fuelConsumption} L/100km" else "" + if (trip.estimatedDrivingDistanceKm != null) " • ${trip.estimatedDrivingDistanceKm.toInt()} km" else ""
                    com.example.traveltool.data.TravelMode.MICROBUS -> "Microbus" + if (trip.fuelConsumption != null) " • ${trip.fuelConsumption} L/100km" else "" + if (trip.estimatedDrivingDistanceKm != null) " • ${trip.estimatedDrivingDistanceKm.toInt()} km" else ""
                    com.example.traveltool.data.TravelMode.PLANE -> "Plane"
                },
                onClick = { onTravelSettings(tripId) }
            )

            HorizontalDivider(color = DraculaCurrent)

            // --- Daily Activities ---
            SettingsRow(
                title = "Daily Activities",
                subtitle = "Day-by-day activity planning",
                onClick = { onDailyActivities(tripId) }
            )

            HorizontalDivider(color = DraculaCurrent)

            // --- Spendings ---
            SettingsRow(
                title = "Spendings",
                subtitle = "Daily & other expenses",
                onClick = { onSpendings(tripId) }
            )

            HorizontalDivider(color = DraculaCurrent)

            // --- Display Currency ---
            SettingsRow(
                title = "Display Currency",
                subtitle = trip.displayCurrency,
                onClick = { showCurrencyDialog = true }
            )

            HorizontalDivider(color = DraculaCurrent)

            // --- Currencies & Exchange Rates ---
            SettingsRow(
                title = "Currencies & Exchange Rates",
                subtitle = "Manage currencies, edit rates",
                onClick = { onCurrencySettings(tripId) }
            )

            HorizontalDivider(color = DraculaCurrent)
        }
    }

    // --- Rename dialog ---
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Trip") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Trip name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DraculaPurple,
                        focusedLabelColor = DraculaPurple,
                        cursorColor = DraculaPurple,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            tripViewModel.updateTrip(trip.copy(name = newName.trim()))
                            showRenameDialog = false
                        }
                    }
                ) { Text("OK", color = DraculaGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
            containerColor = DraculaCurrent,
            titleContentColor = DraculaForeground,
            textContentColor = DraculaForeground,
        )
    }

    // --- Edit Location & Dates dialog ---
    if (showDatesDialog) {
        var editStartMillis by remember { mutableStateOf(trip.startMillis) }
        var editEndMillis by remember { mutableStateOf(trip.endMillis) }
        var showStartDatePicker by remember { mutableStateOf(false) }
        var showEndDatePicker by remember { mutableStateOf(false) }

        val editDateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
        val editStartStr = remember(editStartMillis) { editDateFormat.format(Date(editStartMillis)) }
        val editEndStr = remember(editEndMillis) { editDateFormat.format(Date(editEndMillis)) }

        AlertDialog(
            onDismissRequest = { showDatesDialog = false },
            title = { Text("Edit Location & Dates") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newLocation,
                        onValueChange = { newLocation = it },
                        label = { Text("Location") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DraculaPurple,
                            focusedLabelColor = DraculaPurple,
                            cursorColor = DraculaPurple,
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Start Date", fontSize = 13.sp, color = DraculaComment)
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("📅 $editStartStr", color = DraculaOrange)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("End Date", fontSize = 13.sp, color = DraculaComment)
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("📅 $editEndStr", color = DraculaOrange)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newLocation.isNotBlank() && editEndMillis >= editStartMillis) {
                            tripViewModel.updateTrip(
                                trip.copy(
                                    location = newLocation.trim(),
                                    startMillis = editStartMillis,
                                    endMillis = editEndMillis
                                )
                            )
                            showDatesDialog = false
                        }
                    }
                ) { Text("OK", color = DraculaGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showDatesDialog = false }) { Text("Cancel") }
            },
            containerColor = DraculaCurrent,
            titleContentColor = DraculaForeground,
            textContentColor = DraculaForeground,
        )

        // Start date picker
        if (showStartDatePicker) {
            val startPickerState = rememberDatePickerState(
                initialSelectedDateMillis = editStartMillis
            )
            DatePickerDialog(
                onDismissRequest = { showStartDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        startPickerState.selectedDateMillis?.let { editStartMillis = it }
                        showStartDatePicker = false
                    }) { Text("OK", color = DraculaGreen) }
                },
                dismissButton = {
                    TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
                },
                colors = DatePickerDefaults.colors(containerColor = DraculaCurrent),
            ) {
                DatePicker(
                    state = startPickerState,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(
                        containerColor = DraculaCurrent,
                        selectedDayContainerColor = DraculaPurple,
                        todayDateBorderColor = DraculaPurple,
                        todayContentColor = DraculaPurple,
                    ),
                )
            }
        }

        // End date picker
        if (showEndDatePicker) {
            val endPickerState = rememberDatePickerState(
                initialSelectedDateMillis = editEndMillis
            )
            DatePickerDialog(
                onDismissRequest = { showEndDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        endPickerState.selectedDateMillis?.let { editEndMillis = it }
                        showEndDatePicker = false
                    }) { Text("OK", color = DraculaGreen) }
                },
                dismissButton = {
                    TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
                },
                colors = DatePickerDefaults.colors(containerColor = DraculaCurrent),
            ) {
                DatePicker(
                    state = endPickerState,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(
                        containerColor = DraculaCurrent,
                        selectedDayContainerColor = DraculaPurple,
                        todayDateBorderColor = DraculaPurple,
                        todayContentColor = DraculaPurple,
                    ),
                )
            }
        }
    }

    // --- Starting Point dialog ---
    if (showStartingPointDialog) {
        AlertDialog(
            onDismissRequest = { showStartingPointDialog = false },
            title = { Text("Starting Point") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newStartingPoint,
                        onValueChange = { newStartingPoint = it },
                        label = { Text("Starting point") },
                        placeholder = { Text("e.g. Budapest, Hungary") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DraculaPurple,
                            focusedLabelColor = DraculaPurple,
                            cursorColor = DraculaPurple,
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                detectCurrentLocation(context, scope) { detected ->
                                    newStartingPoint = detected
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("📍 Detect my location", color = DraculaCyan)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tripViewModel.updateTrip(trip.copy(startingPoint = newStartingPoint.trim()))
                        showStartingPointDialog = false
                    }
                ) { Text("OK", color = DraculaGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showStartingPointDialog = false }) { Text("Cancel") }
            },
            containerColor = DraculaCurrent,
            titleContentColor = DraculaForeground,
            textContentColor = DraculaForeground,
        )
    }

    // --- Display Currency dialog ---
    if (showCurrencyDialog) {
        val currencyList = remember { CurrencyManager.getCurrencyList(context) }
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Display Currency") },
            text = {
                Column {
                    Text("Select the currency for trip totals.", fontSize = 13.sp, color = DraculaComment)
                    Spacer(Modifier.height(12.dp))
                    currencyList.forEach { code ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tripViewModel.updateTrip(trip.copy(displayCurrency = code))
                                    showCurrencyDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = trip.displayCurrency == code,
                                onClick = {
                                    tripViewModel.updateTrip(trip.copy(displayCurrency = code))
                                    showCurrencyDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = DraculaPurple),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(code, fontSize = 16.sp, color = DraculaForeground)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text("Close") }
            },
            containerColor = DraculaCurrent,
            titleContentColor = DraculaForeground,
            textContentColor = DraculaForeground,
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    showWarning: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = DraculaForeground,
                )
                if (showWarning) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = DraculaYellow,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = DraculaComment,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = DraculaComment,
        )
    }
}

/**
 * Helper to detect current location and reverse-geocode it.
 */
@Suppress("MissingPermission")
private fun detectCurrentLocation(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onResult: (String) -> Unit
) {
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    fusedClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            scope.launch {
                val name = withContext(Dispatchers.IO) {
                    try {
                        @Suppress("DEPRECATION")
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            listOfNotNull(addr.locality, addr.adminArea, addr.countryName)
                                .joinToString(", ")
                        } else {
                            "${location.latitude}, ${location.longitude}"
                        }
                    } catch (_: Exception) {
                        "${location.latitude}, ${location.longitude}"
                    }
                }
                onResult(name)
            }
        }
    }
}
