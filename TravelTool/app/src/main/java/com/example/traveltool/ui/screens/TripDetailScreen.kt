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
import com.example.traveltool.data.TravelDayPosition
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

    // Activity costs
    trip.activities.forEach { act ->
        if (act.cost > 0) {
            val actCost = act.cost * act.costMultiplier
            total += CurrencyManager.convert(actCost, act.costCurrency, trip.displayCurrency, eurRates)
        }
    }

    // Fuel cost (if consumption + price are available)
    if (trip.fuelConsumption != null && trip.fuelPricePerLiter != null) {
        // Activity-related driving distances (to each activity + return)
        var totalKm = 0.0
        trip.activities.forEach { act ->
            totalKm += act.drivingDistanceToKm ?: 0.0
            totalKm += act.returnDrivingDistanceKm ?: 0.0
        }
        // Moving-day base drives (from DayPlans, for days with no before-arrival activities)
        trip.dayPlans.forEach { plan ->
            val dayHasBeforeArrival = trip.activities.any { act ->
                act.dayMillis == plan.dayMillis &&
                    act.travelDayPosition == TravelDayPosition.BEFORE_ARRIVAL.name
            }
            if (!dayHasBeforeArrival && plan.movingDayDrivingDistanceKm != null) {
                totalKm += plan.movingDayDrivingDistanceKm
            }
        }
        val litres = (totalKm / 100.0) * trip.fuelConsumption
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
    val colors = LocalAppColors.current
    val trip = tripViewModel.getTripById(tripId)

    if (trip == null) {
        onBack()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(trip.name) }

    var showDatesDialog by remember { mutableStateOf(false) }

    var showStartingPointDialog by remember { mutableStateOf(false) }
    var newStartingPoint by remember { mutableStateOf(trip.startingPoint) }

    var showEndingPointDialog by remember { mutableStateOf(false) }
    var newEndingPoint by remember { mutableStateOf(trip.endingPoint) }

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
    // Track which dialog triggered the permission request
    var locationDetectTarget by remember { mutableStateOf("start") }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            detectCurrentLocation(context, scope) { detected ->
                if (locationDetectTarget == "start") {
                    newStartingPoint = detected
                    tripViewModel.updateTrip(trip.copy(startingPoint = detected))
                } else {
                    newEndingPoint = detected
                    tripViewModel.updateTrip(trip.copy(endingPoint = detected))
                }
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
                                tint = colors.yellow,
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
                        showDatesDialog = true
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "$startDate – $endDate",
                    fontSize = 14.sp,
                    color = colors.orange,
                )
                if (trip.location.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "📍 ${trip.location}",
                        fontSize = 14.sp,
                        color = colors.comment,
                    )
                }
            }

            HorizontalDivider(color = colors.current)

            // --- Total Cost Summary ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.15f)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Total Trip Cost", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "💰 ${CurrencyManager.formatAmount(totalCost, trip.displayCurrency, eurRates)} ${trip.displayCurrency}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.green,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Based on accommodations, tolls, tickets, fees, fuel & spendings", fontSize = 11.sp, color = colors.comment)
                }
            }

            HorizontalDivider(color = colors.current)

            // --- Start Point ---
            SettingsRow(
                title = "Start Point",
                subtitle = trip.startingPoint.ifBlank { "Not set" },
                onClick = {
                    newStartingPoint = trip.startingPoint
                    showStartingPointDialog = true
                }
            )

            HorizontalDivider(color = colors.current)

            // --- End Point ---
            SettingsRow(
                title = "End Point",
                subtitle = trip.endingPoint.ifBlank { trip.startingPoint.ifBlank { "Not set" } },
                onClick = {
                    newEndingPoint = trip.endingPoint.ifBlank { trip.startingPoint }
                    showEndingPointDialog = true
                }
            )

            HorizontalDivider(color = colors.current)

            // --- Accommodation ---
            SettingsRow(
                title = "Accommodation",
                subtitle = "${trip.accommodations.size} accommodation(s)",
                showWarning = trip.accommodations.isEmpty() || trip.accommodations.any { it.hasWarning },
                onClick = { onAccommodation(tripId) }
            )

            HorizontalDivider(color = colors.current)

            // --- Travel Mode ---
            val activityTotalKm = run {
                var km = 0.0
                trip.activities.forEach { act ->
                    km += act.drivingDistanceToKm ?: 0.0
                    km += act.returnDrivingDistanceKm ?: 0.0
                }
                trip.dayPlans.forEach { plan ->
                    val dayHasBeforeArrival = trip.activities.any { act ->
                        act.dayMillis == plan.dayMillis &&
                            act.travelDayPosition == TravelDayPosition.BEFORE_ARRIVAL.name
                    }
                    if (!dayHasBeforeArrival && plan.movingDayDrivingDistanceKm != null) {
                        km += plan.movingDayDrivingDistanceKm
                    }
                }
                km
            }
            SettingsRow(
                title = "Travel Mode",
                subtitle = when (trip.travelMode) {
                    com.example.traveltool.data.TravelMode.CAR -> "Car" + if (trip.fuelConsumption != null) " • ${trip.fuelConsumption} L/100km" else "" + if (activityTotalKm > 0) " • ${activityTotalKm.toInt()} km" else ""
                    com.example.traveltool.data.TravelMode.MICROBUS -> "Microbus" + if (trip.fuelConsumption != null) " • ${trip.fuelConsumption} L/100km" else "" + if (activityTotalKm > 0) " • ${activityTotalKm.toInt()} km" else ""
                    com.example.traveltool.data.TravelMode.PLANE -> "Plane"
                },
                onClick = { onTravelSettings(tripId) }
            )

            HorizontalDivider(color = colors.current)

            // --- Daily Activities ---
            SettingsRow(
                title = "Daily Activities",
                subtitle = "Day-by-day activity planning",
                onClick = { onDailyActivities(tripId) }
            )

            HorizontalDivider(color = colors.current)

            // --- Spendings ---
            SettingsRow(
                title = "Spendings",
                subtitle = "Daily & other expenses",
                onClick = { onSpendings(tripId) }
            )

            HorizontalDivider(color = colors.current)

            // --- Display Currency ---
            SettingsRow(
                title = "Display Currency",
                subtitle = trip.displayCurrency,
                onClick = { showCurrencyDialog = true }
            )

            HorizontalDivider(color = colors.current)

            // --- Currencies & Exchange Rates ---
            SettingsRow(
                title = "Currencies & Exchange Rates",
                subtitle = "Manage currencies, edit rates",
                onClick = { onCurrencySettings(tripId) }
            )

            HorizontalDivider(color = colors.current)
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
                        focusedBorderColor = colors.primary,
                        focusedLabelColor = colors.primary,
                        cursorColor = colors.primary,
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
                ) { Text("OK", color = colors.green) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
            containerColor = colors.current,
            titleContentColor = colors.foreground,
            textContentColor = colors.foreground,
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
            title = { Text("Edit Dates") },
            text = {
                Column {
                    Text("Start Date", fontSize = 13.sp, color = colors.comment)
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("📅 $editStartStr", color = colors.orange)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("End Date", fontSize = 13.sp, color = colors.comment)
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("📅 $editEndStr", color = colors.orange)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editEndMillis >= editStartMillis) {
                            tripViewModel.updateTrip(
                                trip.copy(
                                    startMillis = editStartMillis,
                                    endMillis = editEndMillis
                                )
                            )
                            showDatesDialog = false
                        }
                    }
                ) { Text("OK", color = colors.green) }
            },
            dismissButton = {
                TextButton(onClick = { showDatesDialog = false }) { Text("Cancel") }
            },
            containerColor = colors.current,
            titleContentColor = colors.foreground,
            textContentColor = colors.foreground,
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
                    }) { Text("OK", color = colors.green) }
                },
                dismissButton = {
                    TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
                },
                colors = DatePickerDefaults.colors(containerColor = colors.current),
            ) {
                DatePicker(
                    state = startPickerState,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(
                        containerColor = colors.current,
                        selectedDayContainerColor = colors.primary,
                        todayDateBorderColor = colors.primary,
                        todayContentColor = colors.primary,
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
                    }) { Text("OK", color = colors.green) }
                },
                dismissButton = {
                    TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
                },
                colors = DatePickerDefaults.colors(containerColor = colors.current),
            ) {
                DatePicker(
                    state = endPickerState,
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(
                        containerColor = colors.current,
                        selectedDayContainerColor = colors.primary,
                        todayDateBorderColor = colors.primary,
                        todayContentColor = colors.primary,
                    ),
                )
            }
        }
    }

    // --- Start Point dialog ---
    if (showStartingPointDialog) {
        AlertDialog(
            onDismissRequest = { showStartingPointDialog = false },
            title = { Text("Start Point") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newStartingPoint,
                        onValueChange = { newStartingPoint = it },
                        label = { Text("Start point") },
                        placeholder = { Text("e.g. Budapest, Hungary") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            focusedLabelColor = colors.primary,
                            cursorColor = colors.primary,
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
                                locationDetectTarget = "start"
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("📍 Detect my location", color = colors.accent)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tripViewModel.updateTrip(trip.copy(startingPoint = newStartingPoint.trim()))
                        showStartingPointDialog = false
                    }
                ) { Text("OK", color = colors.green) }
            },
            dismissButton = {
                TextButton(onClick = { showStartingPointDialog = false }) { Text("Cancel") }
            },
            containerColor = colors.current,
            titleContentColor = colors.foreground,
            textContentColor = colors.foreground,
        )
    }

    // --- End Point dialog ---
    if (showEndingPointDialog) {
        AlertDialog(
            onDismissRequest = { showEndingPointDialog = false },
            title = { Text("End Point") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newEndingPoint,
                        onValueChange = { newEndingPoint = it },
                        label = { Text("End point") },
                        placeholder = { Text("e.g. Budapest, Hungary") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            focusedLabelColor = colors.primary,
                            cursorColor = colors.primary,
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
                                    newEndingPoint = detected
                                }
                            } else {
                                locationDetectTarget = "end"
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("📍 Detect my location", color = colors.accent)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { newEndingPoint = trip.startingPoint },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("📋 Same as start point", color = colors.accent)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tripViewModel.updateTrip(trip.copy(endingPoint = newEndingPoint.trim()))
                        showEndingPointDialog = false
                    }
                ) { Text("OK", color = colors.green) }
            },
            dismissButton = {
                TextButton(onClick = { showEndingPointDialog = false }) { Text("Cancel") }
            },
            containerColor = colors.current,
            titleContentColor = colors.foreground,
            textContentColor = colors.foreground,
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
                    Text("Select the currency for trip totals.", fontSize = 13.sp, color = colors.comment)
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
                                colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(code, fontSize = 16.sp, color = colors.foreground)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text("Close") }
            },
            containerColor = colors.current,
            titleContentColor = colors.foreground,
            textContentColor = colors.foreground,
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
    val colors = LocalAppColors.current
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
                    color = colors.foreground,
                )
                if (showWarning) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = colors.yellow,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = colors.comment,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.comment,
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
