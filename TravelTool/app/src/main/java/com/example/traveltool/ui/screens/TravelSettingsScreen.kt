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
import androidx.compose.material.icons.filled.Search
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
import kotlinx.coroutines.launch

/**
 * Travel mode settings: car/plane mode, fuel, tolls, plane tickets, additional fees.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelSettingsScreen(
    tripId: String,
    tripViewModel: TripViewModel,
    onApiKeySettings: () -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val trip = tripViewModel.getTripById(tripId)

    if (trip == null) {
        onBack()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currencies = remember { mutableStateOf(CurrencyManager.getCurrencyList(context)) }

    LaunchedEffect(Unit) {
        currencies.value = CurrencyManager.getCurrencyList(context)
    }

    var fuelText by remember { mutableStateOf(trip.fuelConsumption?.toString() ?: "") }
    var fuelPriceText by remember { mutableStateOf(trip.fuelPricePerLiter?.toString() ?: "") }

    var showAddTollDialog by remember { mutableStateOf(false) }
    var editingToll by remember { mutableStateOf<TollRoad?>(null) }
    var tollToDelete by remember { mutableStateOf<TollRoad?>(null) }
    var isSearchingTolls by remember { mutableStateOf(false) }

    var showAddTicketDialog by remember { mutableStateOf(false) }
    var editingTicket by remember { mutableStateOf<PlaneTicket?>(null) }
    var ticketToDelete by remember { mutableStateOf<PlaneTicket?>(null) }

    var showAddCarRentalDialog by remember { mutableStateOf(false) }
    var editingCarRental by remember { mutableStateOf<CarRental?>(null) }
    var carRentalToDelete by remember { mutableStateOf<CarRental?>(null) }

    var showAddPublicTransportDialog by remember { mutableStateOf(false) }
    var editingPublicTransport by remember { mutableStateOf<PublicTransportFee?>(null) }
    var publicTransportToDelete by remember { mutableStateOf<PublicTransportFee?>(null) }

    var showAddFeeDialog by remember { mutableStateOf(false) }
    var editingFee by remember { mutableStateOf<AdditionalFee?>(null) }
    var feeToDelete by remember { mutableStateOf<AdditionalFee?>(null) }

    var showMissingKeyDialog by remember { mutableStateOf(false) }
    var isEstimatingFuel by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Travel Mode") },
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
            // ── Travel Mode ─────────────────────────────────
            item {
                SectionHeader("Travel Mode")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TravelMode.entries.forEach { mode ->
                        FilterChip(
                            selected = trip.travelMode == mode,
                            onClick = { tripViewModel.updateTrip(trip.copy(travelMode = mode)) },
                            label = { Text(when (mode) { TravelMode.CAR -> "🚗 Car"; TravelMode.MICROBUS -> "🚐 Microbus"; TravelMode.PLANE -> "✈️ Plane" }) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary, selectedLabelColor = colors.foreground),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ══════════════ CAR / MICROBUS MODE ══════════════
            if (trip.travelMode == TravelMode.CAR || trip.travelMode == TravelMode.MICROBUS) {
                item {
                    OutlinedTextField(
                        value = fuelText,
                        onValueChange = { fuelText = it; tripViewModel.updateTrip(trip.copy(fuelConsumption = it.toDoubleOrNull())) },
                        label = { Text("Fuel consumption (L/100km)") },
                        placeholder = { Text("e.g. 7.5") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        colors = themedTextFieldColors(),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = fuelPriceText,
                            onValueChange = { fuelPriceText = it; tripViewModel.updateTrip(trip.copy(fuelPricePerLiter = it.toDoubleOrNull())) },
                            label = { Text("Fuel price / liter") },
                            placeholder = { Text("e.g. 1.65") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            colors = themedTextFieldColors(),
                        )
                        CurrencyPicker(
                            selected = trip.fuelPriceCurrency,
                            currencies = currencies.value,
                            onSelect = { tripViewModel.updateTrip(trip.copy(fuelPriceCurrency = it)) },
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Fuel Cost Estimation ────────────────────────
                item {
                    val canEstimate = trip.fuelConsumption != null && trip.fuelConsumption > 0 &&
                            trip.fuelPricePerLiter != null && trip.fuelPricePerLiter > 0

                    Button(
                        onClick = {
                            val apiKey = ApiKeyStore.getOpenAiKey(context)
                            if (apiKey.isBlank()) {
                                showMissingKeyDialog = true
                                return@Button
                            }
                            val model = ApiKeyStore.getOpenAiModel(context)
                            if (trip.startingPoint.isBlank()) { Toast.makeText(context, "Set a starting point first", Toast.LENGTH_SHORT).show(); return@Button }
                            if (trip.accommodations.isEmpty()) { Toast.makeText(context, "Add accommodations first", Toast.LENGTH_SHORT).show(); return@Button }
                            isEstimatingFuel = true
                            scope.launch {
                                val distanceKm = DirectionsApiHelper.estimateDrivingDistance(
                                    startingPoint = trip.startingPoint,
                                    endingPoint = trip.endingPoint,
                                    accommodations = trip.accommodations,
                                    openAiApiKey = apiKey,
                                    travelMode = trip.travelMode,
                                    model = model
                                )
                                if (distanceKm != null) {
                                    tripViewModel.updateTrip(trip.copy(estimatedDrivingDistanceKm = distanceKm))
                                    Toast.makeText(context, "Estimated distance: ${distanceKm.toInt()} km", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Could not estimate distance — check API key", Toast.LENGTH_LONG).show()
                                }
                                isEstimatingFuel = false
                            }
                        },
                        enabled = canEstimate && !isEstimatingFuel,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background),
                    ) {
                        if (isEstimatingFuel) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.background)
                        } else {
                            Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEstimatingFuel) "Estimating…" else "Estimate Fuel Cost")
                    }

                    // Show result if we have an estimated distance
                    if (trip.estimatedDrivingDistanceKm != null && trip.fuelConsumption != null && trip.fuelPricePerLiter != null) {
                        val routeKm = trip.estimatedDrivingDistanceKm
                        val activityKm = trip.activities.sumOf { (it.drivingDistanceToKm ?: 0.0) + (it.returnDrivingDistanceKm ?: 0.0) }
                        val totalKm = routeKm + activityKm
                        val litres = (totalKm / 100.0) * trip.fuelConsumption
                        val cost = litres * trip.fuelPricePerLiter

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.green.copy(alpha = 0.12f)),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Estimated Fuel Cost", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.green)
                                Spacer(Modifier.height(6.dp))
                                Text("\uD83D\uDEE3\uFE0F Route distance: ${routeKm.toInt()} km", fontSize = 14.sp, color = colors.foreground)
                                if (activityKm > 0) {
                                    Text("\uD83D\uDCCD Activity drives: ${activityKm.toInt()} km", fontSize = 14.sp, color = colors.foreground)
                                }
                                Text("\uD83D\uDEE3\uFE0F Total distance: ${totalKm.toInt()} km", fontSize = 14.sp, color = colors.foreground)
                                Text("\u26FD Fuel needed: %.1f L".format(litres), fontSize = 14.sp, color = colors.foreground)
                                Text(
                                    "\uD83D\uDCB0 Cost: %.2f %s".format(cost, trip.fuelPriceCurrency),
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.green,
                                )
                            }
                        }
                    } else if (!canEstimate) {
                        Text(
                            "Enter fuel consumption and price to estimate fuel cost",
                            fontSize = 12.sp, color = colors.comment,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                }

                item { HorizontalDivider(color = colors.current); SectionHeader("Toll Roads, Vignettes & Ferries") }

                if (trip.tollRoads.isEmpty()) {
                    item { Text("No toll roads added.", fontSize = 14.sp, color = colors.comment, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
                }
                items(trip.tollRoads, key = { it.id }) { toll ->
                    PriceItemCard(toll.name, toll.price, toll.currency, if (toll.isAutoGenerated) "AI" else null, { editingToll = toll }, { tollToDelete = toll })
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showAddTollDialog = true }, Modifier.weight(1f)) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add Toll")
                        }
                        Button(
                            onClick = {
                                val apiKey = ApiKeyStore.getOpenAiKey(context)
                                if (apiKey.isBlank()) {
                                    showMissingKeyDialog = true
                                    return@Button
                                }
                                val model = ApiKeyStore.getOpenAiModel(context)
                                if (trip.startingPoint.isBlank()) { Toast.makeText(context, "Set a starting point first", Toast.LENGTH_SHORT).show(); return@Button }
                                if (trip.accommodations.isEmpty()) { Toast.makeText(context, "Add accommodations first", Toast.LENGTH_SHORT).show(); return@Button }
                                isSearchingTolls = true
                                scope.launch {
                                    val autoTolls = DirectionsApiHelper.findTollsForTrip(
                                        startingPoint = trip.startingPoint,
                                        endingPoint = trip.endingPoint,
                                        accommodations = trip.accommodations,
                                        openAiApiKey = apiKey,
                                        travelMode = trip.travelMode,
                                        tripStartMillis = trip.startMillis,
                                        tripEndMillis = trip.endMillis,
                                        model = model
                                    )
                                    val userTolls = trip.tollRoads.filter { !it.isAutoGenerated }
                                    tripViewModel.updateTrip(trip.copy(tollRoads = userTolls + autoTolls))
                                    isSearchingTolls = false
                                    Toast.makeText(context, if (autoTolls.isNotEmpty()) "Found ${autoTolls.size} toll(s) via AI" else "No tolls found — check API key or route", Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = !isSearchingTolls, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background),
                        ) {
                            if (isSearchingTolls) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.background)
                            else Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text(if (isSearchingTolls) "AI…" else "Find tolls")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ══════════════ PLANE MODE ══════════════
            if (trip.travelMode == TravelMode.PLANE) {
                item { HorizontalDivider(color = colors.current); SectionHeader("Plane Tickets") }
                if (trip.planeTickets.isEmpty()) {
                    item { Text("No plane tickets added.", fontSize = 14.sp, color = colors.comment, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
                }
                items(trip.planeTickets, key = { it.id }) { ticket ->
                    val label = buildString {
                        val from = ticket.fromAirport.ifBlank { null }
                        val to = ticket.toAirport.ifBlank { null }
                        if (from != null || to != null) {
                            append(from ?: "?"); append(" → "); append(to ?: "?")
                        } else {
                            append(ticket.name.ifBlank { "(No route)" })
                        }
                        if (ticket.quantity > 1) append("  ×${ticket.quantity}")
                    }
                    PriceItemCard(label, ticket.price * ticket.quantity, ticket.currency, if (ticket.quantity > 1) "${ticket.quantity} pcs" else null, { editingTicket = ticket }, { ticketToDelete = ticket })
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showAddTicketDialog = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add Plane Ticket")
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Car Rental ──────────────────────────────
                item { HorizontalDivider(color = colors.current); SectionHeader("Car Rental") }
                if (trip.carRentals.isEmpty()) {
                    item { Text("No car rentals added.", fontSize = 14.sp, color = colors.comment, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
                }
                items(trip.carRentals, key = { it.id }) { rental ->
                    PriceItemCard(rental.name, rental.price, rental.currency, null, { editingCarRental = rental }, { carRentalToDelete = rental })
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showAddCarRentalDialog = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add Car Rental")
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ── Public Transport (shown when no car rental) ─
                if (trip.carRentals.isEmpty()) {
                    item { HorizontalDivider(color = colors.current); SectionHeader("Public Transport") }
                    if (trip.publicTransportFees.isEmpty()) {
                        item { Text("No public transport fees.", fontSize = 14.sp, color = colors.comment, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
                    }
                    items(trip.publicTransportFees, key = { it.id }) { ptf ->
                        PriceItemCard(ptf.description, ptf.price, ptf.currency, if (ptf.isAutoGenerated) "auto" else null, { editingPublicTransport = ptf }, { publicTransportToDelete = ptf })
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showAddPublicTransportDialog = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add Transport Fee")
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            // ══════════════ ADDITIONAL FEES ══════════════
            item { HorizontalDivider(color = colors.current); SectionHeader("Additional Fees") }
            if (trip.additionalFees.isEmpty()) {
                item { Text("No additional fees.", fontSize = 14.sp, color = colors.comment, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
            }
            items(trip.additionalFees, key = { it.id }) { fee ->
                PriceItemCard(fee.name, fee.price, fee.currency, null, { editingFee = fee }, { feeToDelete = fee })
            }
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showAddFeeDialog = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add Fee")
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    // ════════════════ DIALOGS ════════════════

    if (showAddTollDialog) {
        PriceEditDialog("Add Toll", "", "", currencies.value, trip.displayCurrency,
            { n, p, c -> tripViewModel.updateTrip(trip.copy(tollRoads = trip.tollRoads + TollRoad(name = n, price = p, currency = c, isAutoGenerated = false))); showAddTollDialog = false },
            { showAddTollDialog = false })
    }
    editingToll?.let { t ->
        PriceEditDialog("Edit Toll", t.name, if (t.price > 0) t.price.toString() else "", currencies.value, t.currency,
            { n, p, c -> tripViewModel.updateTrip(trip.copy(tollRoads = trip.tollRoads.map { if (it.id == t.id) it.copy(name = n, price = p, currency = c) else it })); editingToll = null },
            { editingToll = null })
    }
    tollToDelete?.let { t ->
        DeleteConfirmDialog("Delete Toll", t.name, { tripViewModel.updateTrip(trip.copy(tollRoads = trip.tollRoads.filter { it.id != t.id })); tollToDelete = null }, { tollToDelete = null })
    }

    if (showAddTicketDialog) {
        PlaneTicketEditDialog("Add Plane Ticket", PlaneTicket(), currencies.value, trip.displayCurrency,
            { ticket -> tripViewModel.updateTrip(trip.copy(planeTickets = trip.planeTickets + ticket)); showAddTicketDialog = false },
            { showAddTicketDialog = false })
    }
    editingTicket?.let { t ->
        PlaneTicketEditDialog("Edit Ticket", t, currencies.value, t.currency,
            { ticket -> tripViewModel.updateTrip(trip.copy(planeTickets = trip.planeTickets.map { if (it.id == t.id) ticket.copy(id = t.id) else it })); editingTicket = null },
            { editingTicket = null })
    }
    ticketToDelete?.let { t ->
        val label = t.fromAirport.ifBlank { null }?.let { from -> "${from} → ${t.toAirport.ifBlank { "?" }}" } ?: t.name
        DeleteConfirmDialog("Delete Ticket", label, { tripViewModel.updateTrip(trip.copy(planeTickets = trip.planeTickets.filter { it.id != t.id })); ticketToDelete = null }, { ticketToDelete = null })
    }

    // Car Rental dialogs
    if (showAddCarRentalDialog) {
        PriceEditDialog("Add Car Rental", "", "", currencies.value, trip.displayCurrency,
            { n, p, c -> tripViewModel.updateTrip(trip.copy(carRentals = trip.carRentals + CarRental(name = n, price = p, currency = c))); showAddCarRentalDialog = false },
            { showAddCarRentalDialog = false })
    }
    editingCarRental?.let { r ->
        PriceEditDialog("Edit Car Rental", r.name, if (r.price > 0) r.price.toString() else "", currencies.value, r.currency,
            { n, p, c -> tripViewModel.updateTrip(trip.copy(carRentals = trip.carRentals.map { if (it.id == r.id) it.copy(name = n, price = p, currency = c) else it })); editingCarRental = null },
            { editingCarRental = null })
    }
    carRentalToDelete?.let { r ->
        DeleteConfirmDialog("Delete Car Rental", r.name, { tripViewModel.updateTrip(trip.copy(carRentals = trip.carRentals.filter { it.id != r.id })); carRentalToDelete = null }, { carRentalToDelete = null })
    }

    // Public Transport dialogs
    if (showAddPublicTransportDialog) {
        PriceEditDialog("Add Transport Fee", "", "", currencies.value, trip.displayCurrency,
            { n, p, c -> tripViewModel.updateTrip(trip.copy(publicTransportFees = trip.publicTransportFees + PublicTransportFee(description = n, price = p, currency = c, isAutoGenerated = false))); showAddPublicTransportDialog = false },
            { showAddPublicTransportDialog = false })
    }
    editingPublicTransport?.let { ptf ->
        PriceEditDialog("Edit Transport Fee", ptf.description, if (ptf.price > 0) ptf.price.toString() else "", currencies.value, ptf.currency,
            { n, p, c -> tripViewModel.updateTrip(trip.copy(publicTransportFees = trip.publicTransportFees.map { if (it.id == ptf.id) it.copy(description = n, price = p, currency = c, isAutoGenerated = false) else it })); editingPublicTransport = null },
            { editingPublicTransport = null })
    }
    publicTransportToDelete?.let { ptf ->
        DeleteConfirmDialog("Delete Transport Fee", ptf.description, { tripViewModel.updateTrip(trip.copy(publicTransportFees = trip.publicTransportFees.filter { it.id != ptf.id })); publicTransportToDelete = null }, { publicTransportToDelete = null })
    }

    if (showAddFeeDialog) {
        PriceEditDialog("Add Fee", "", "", currencies.value, trip.displayCurrency,
            { n, p, c -> tripViewModel.updateTrip(trip.copy(additionalFees = trip.additionalFees + AdditionalFee(name = n, price = p, currency = c))); showAddFeeDialog = false },
            { showAddFeeDialog = false })
    }
    editingFee?.let { f ->
        PriceEditDialog("Edit Fee", f.name, if (f.price > 0) f.price.toString() else "", currencies.value, f.currency,
            { n, p, c -> tripViewModel.updateTrip(trip.copy(additionalFees = trip.additionalFees.map { if (it.id == f.id) it.copy(name = n, price = p, currency = c) else it })); editingFee = null },
            { editingFee = null })
    }
    feeToDelete?.let { f ->
        DeleteConfirmDialog("Delete Fee", f.name, { tripViewModel.updateTrip(trip.copy(additionalFees = trip.additionalFees.filter { it.id != f.id })); feeToDelete = null }, { feeToDelete = null })
    }

    if (showMissingKeyDialog) {
        AlertDialog(
            onDismissRequest = { showMissingKeyDialog = false },
            title = { Text("API Key Required") },
            text = { Text("To use automatic toll finding you need to set your OpenAI API key.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMissingKeyDialog = false
                        onApiKeySettings()
                    }
                ) { Text("Set API Key", color = colors.green) }
            },
            dismissButton = {
                TextButton(onClick = { showMissingKeyDialog = false }) { Text("Cancel") }
            },
            containerColor = colors.current,
            titleContentColor = colors.foreground,
            textContentColor = colors.foreground,
        )
    }
}

// ════════════════ REUSABLE COMPOSABLES ════════════════

@Composable
private fun SectionHeader(text: String) {
    val colors = LocalAppColors.current
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
}

@Composable
private fun themedTextFieldColors(): TextFieldColors {
    val colors = LocalAppColors.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.primary, focusedLabelColor = colors.primary, cursorColor = colors.primary,
    )
}


@Composable
private fun PriceItemCard(name: String, price: Double, currency: String, badge: String? = null, onClick: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).clickable { onClick() },
        shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = colors.current),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name.ifBlank { "(No name)" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.foreground)
                    if (badge != null) { Spacer(Modifier.width(6.dp)); Text(badge, fontSize = 10.sp, color = colors.accent) }
                }
                Text(if (price > 0) "💰 $price $currency" else "Price not set", fontSize = 13.sp,
                    color = if (price > 0) colors.green else colors.comment)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Delete", tint = colors.red, modifier = Modifier.size(18.dp))
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceEditDialog(
    title: String, initialName: String, initialPrice: String,
    currencies: List<String>, initialCurrency: String,
    onConfirm: (name: String, price: Double, currency: String) -> Unit, onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    var name by remember { mutableStateOf(initialName) }
    var priceText by remember { mutableStateOf(initialPrice) }
    var currency by remember { mutableStateOf(initialCurrency) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = themedTextFieldColors())
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = priceText, onValueChange = { priceText = it }, label = { Text("Price") },
                        placeholder = { Text("0.00") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = themedTextFieldColors())
                    CurrencyPicker(selected = currency, currencies = currencies, onSelect = { currency = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim(), priceText.toDoubleOrNull() ?: 0.0, currency) }) { Text("OK", color = colors.green) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = colors.current, titleContentColor = colors.foreground, textContentColor = colors.foreground,
    )
}

@Composable
private fun DeleteConfirmDialog(title: String, itemName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { Text("Delete \"${itemName.ifBlank { "(unnamed)" }}\"?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = colors.red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = colors.current, titleContentColor = colors.foreground, textContentColor = colors.foreground,
    )
}


/**
 * Specialised dialog for plane tickets with From/To airport search + quantity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaneTicketEditDialog(
    title: String,
    initial: PlaneTicket,
    currencies: List<String>,
    defaultCurrency: String,
    onConfirm: (PlaneTicket) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    var fromQuery by remember { mutableStateOf(initial.fromAirport) }
    var toQuery by remember { mutableStateOf(initial.toAirport) }
    var fromResults by remember { mutableStateOf<List<AirportDatabase.Airport>>(emptyList()) }
    var toResults by remember { mutableStateOf<List<AirportDatabase.Airport>>(emptyList()) }
    var priceText by remember { mutableStateOf(if (initial.price > 0) initial.price.toString() else "") }
    var currency by remember { mutableStateOf(initial.currency.ifBlank { defaultCurrency }) }
    var quantity by remember { mutableStateOf(initial.quantity.coerceIn(1, 20)) }
    var quantityExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                // From airport
                Text("From", fontSize = 12.sp, color = colors.comment)
                OutlinedTextField(
                    value = fromQuery,
                    onValueChange = { fromQuery = it; fromResults = AirportDatabase.search(it) },
                    placeholder = { Text("City or IATA code") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = themedTextFieldColors(),
                )
                if (fromResults.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.current),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            fromResults.forEach { airport ->
                                Text(
                                    airport.displayName,
                                    fontSize = 13.sp, color = colors.foreground,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { fromQuery = airport.shortLabel; fromResults = emptyList() }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // To airport
                Text("To", fontSize = 12.sp, color = colors.comment)
                OutlinedTextField(
                    value = toQuery,
                    onValueChange = { toQuery = it; toResults = AirportDatabase.search(it) },
                    placeholder = { Text("City or IATA code") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = themedTextFieldColors(),
                )
                if (toResults.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.current),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            toResults.forEach { airport ->
                                Text(
                                    airport.displayName,
                                    fontSize = 13.sp, color = colors.foreground,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { toQuery = airport.shortLabel; toResults = emptyList() }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Price + Currency
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = priceText, onValueChange = { priceText = it },
                        label = { Text("Price / ticket") },
                        placeholder = { Text("0.00") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = themedTextFieldColors(),
                    )
                    CurrencyPicker(selected = currency, currencies = currencies, onSelect = { currency = it })
                }

                Spacer(Modifier.height(12.dp))

                // Quantity
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quantity:", fontSize = 14.sp, color = colors.foreground)
                    ExposedDropdownMenuBox(expanded = quantityExpanded, onExpandedChange = { quantityExpanded = it }) {
                        OutlinedTextField(
                            value = quantity.toString(), onValueChange = {}, readOnly = true, singleLine = true,
                            modifier = Modifier.width(80.dp).menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(quantityExpanded) },
                            colors = themedTextFieldColors(),
                        )
                        ExposedDropdownMenu(expanded = quantityExpanded, onDismissRequest = { quantityExpanded = false }) {
                            (1..20).forEach { q ->
                                DropdownMenuItem(text = { Text("$q") }, onClick = { quantity = q; quantityExpanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (fromQuery.isNotBlank() || toQuery.isNotBlank()) {
                    val name = "${fromQuery.ifBlank { "?" }} → ${toQuery.ifBlank { "?" }}"
                    onConfirm(PlaneTicket(
                        name = name,
                        fromAirport = fromQuery.trim(),
                        toAirport = toQuery.trim(),
                        price = priceText.toDoubleOrNull() ?: 0.0,
                        currency = currency,
                        quantity = quantity,
                    ))
                }
            }) { Text("OK", color = colors.green) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = colors.current, titleContentColor = colors.foreground, textContentColor = colors.foreground,
    )
}
