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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
 * Travel specifics: car/plane mode, fuel, tolls, plane tickets, additional fees, daily activities.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelSettingsScreen(
    tripId: String,
    tripViewModel: TripViewModel,
    onApiKeySettings: () -> Unit,
    onDayClick: (Long) -> Unit,
    onBack: () -> Unit
) {
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

    var showAddFeeDialog by remember { mutableStateOf(false) }
    var editingFee by remember { mutableStateOf<AdditionalFee?>(null) }
    var feeToDelete by remember { mutableStateOf<AdditionalFee?>(null) }

    var showMissingKeyDialog by remember { mutableStateOf(false) }
    var isEstimatingFuel by remember { mutableStateOf(false) }

    // Daily activities: generate list of days
    val oneDayMs = 24 * 60 * 60 * 1000L
    val tripDays = remember(trip.startMillis, trip.endMillis) {
        val days = mutableListOf<Long>()
        var d = trip.startMillis
        while (d < trip.endMillis) {
            days.add(d)
            d += oneDayMs
        }
        days
    }
    val hasAnyAccommodation = trip.accommodations.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Travel Specifics") },
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
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DraculaPurple, selectedLabelColor = DraculaForeground),
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
                        colors = draculaTextFieldColors(),
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
                            colors = draculaTextFieldColors(),
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
                        colors = ButtonDefaults.buttonColors(containerColor = DraculaCyan, contentColor = DraculaBackground),
                    ) {
                        if (isEstimatingFuel) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DraculaBackground)
                        } else {
                            Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEstimatingFuel) "Estimating…" else "Estimate Fuel Cost")
                    }

                    // Show result if we have an estimated distance
                    if (trip.estimatedDrivingDistanceKm != null && trip.fuelConsumption != null && trip.fuelPricePerLiter != null) {
                        val distKm = trip.estimatedDrivingDistanceKm
                        val litres = (distKm / 100.0) * trip.fuelConsumption
                        val cost = litres * trip.fuelPricePerLiter

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = DraculaGreen.copy(alpha = 0.12f)),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Estimated Fuel Cost", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DraculaGreen)
                                Spacer(Modifier.height(6.dp))
                                Text("🛣️ Distance: ${distKm.toInt()} km", fontSize = 14.sp, color = DraculaForeground)
                                Text("⛽ Fuel needed: %.1f L".format(litres), fontSize = 14.sp, color = DraculaForeground)
                                Text(
                                    "💰 Cost: %.2f %s".format(cost, trip.fuelPriceCurrency),
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DraculaGreen,
                                )
                            }
                        }
                    } else if (!canEstimate) {
                        Text(
                            "Enter fuel consumption and price to estimate fuel cost",
                            fontSize = 12.sp, color = DraculaComment,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                }

                item { HorizontalDivider(color = DraculaCurrent); SectionHeader("Toll Roads, Vignettes & Ferries") }

                if (trip.tollRoads.isEmpty()) {
                    item { Text("No toll roads added.", fontSize = 14.sp, color = DraculaComment, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
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
                            colors = ButtonDefaults.buttonColors(containerColor = DraculaCyan, contentColor = DraculaBackground),
                        ) {
                            if (isSearchingTolls) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = DraculaBackground)
                            else Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text(if (isSearchingTolls) "AI…" else "Find tolls")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ══════════════ PLANE MODE ══════════════
            if (trip.travelMode == TravelMode.PLANE) {
                item { HorizontalDivider(color = DraculaCurrent); SectionHeader("Plane Tickets") }
                if (trip.planeTickets.isEmpty()) {
                    item { Text("No plane tickets added.", fontSize = 14.sp, color = DraculaComment, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
                }
                items(trip.planeTickets, key = { it.id }) { ticket ->
                    PriceItemCard(ticket.name, ticket.price, ticket.currency, null, { editingTicket = ticket }, { ticketToDelete = ticket })
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showAddTicketDialog = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add Plane Ticket")
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ══════════════ ADDITIONAL FEES ══════════════
            item { HorizontalDivider(color = DraculaCurrent); SectionHeader("Additional Fees") }
            if (trip.additionalFees.isEmpty()) {
                item { Text("No additional fees.", fontSize = 14.sp, color = DraculaComment, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
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

            // ══════════════ DAILY ACTIVITIES ══════════════
            item { HorizontalDivider(color = DraculaCurrent); SectionHeader("Daily Activities") }

            if (!hasAnyAccommodation) {
                item {
                    Text(
                        "Add accommodations to unlock daily activity planning.",
                        fontSize = 14.sp, color = DraculaComment,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(tripDays.size) { index ->
                    val dayMillis = tripDays[index]
                    val dayNumber = index + 1
                    val dayDateFormat = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault())
                    val dayLabel = dayDateFormat.format(java.util.Date(dayMillis))

                    // Check if this day has accommodation coverage
                    val coveringAccom = trip.accommodations.find { accom ->
                        accom.startMillis <= dayMillis && dayMillis < accom.endMillis
                    } ?: trip.accommodations.find { accom ->
                        accom.startMillis <= dayMillis && dayMillis <= accom.endMillis
                    }
                    val hasCoverage = coveringAccom != null && coveringAccom.location.isNotBlank()

                    // Determine moving/staying
                    val prevDayAccom = if (index > 0) {
                        val prevMillis = tripDays[index - 1]
                        trip.accommodations.find { it.startMillis <= prevMillis && prevMillis < it.endMillis }
                            ?: trip.accommodations.find { it.startMillis <= prevMillis && prevMillis <= it.endMillis }
                    } else null

                    val isMoving = when {
                        index == 0 -> true  // first day — traveling from home
                        coveringAccom == null || prevDayAccom == null -> false
                        coveringAccom.location != prevDayAccom.location -> true
                        else -> false
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 3.dp)
                            .then(
                                if (hasCoverage) Modifier.clickable { onDayClick(dayMillis) }
                                else Modifier
                            ),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasCoverage) DraculaCurrent else DraculaCurrent.copy(alpha = 0.4f)
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isMoving) "🚗" else "🏨",
                                fontSize = 20.sp,
                                modifier = Modifier.width(32.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Day $dayNumber — $dayLabel",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (hasCoverage) DraculaForeground else DraculaComment,
                                )
                                if (coveringAccom != null && coveringAccom.location.isNotBlank()) {
                                    Text(
                                        text = coveringAccom.location,
                                        fontSize = 12.sp,
                                        color = DraculaComment,
                                    )
                                } else {
                                    Text(
                                        "No accommodation — tap to add",
                                        fontSize = 12.sp,
                                        color = DraculaYellow.copy(alpha = 0.7f),
                                    )
                                }
                            }
                            if (hasCoverage) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = DraculaComment,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
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
        PriceEditDialog("Add Plane Ticket", "", "", currencies.value, trip.displayCurrency,
            { n, p, c -> tripViewModel.updateTrip(trip.copy(planeTickets = trip.planeTickets + PlaneTicket(name = n, price = p, currency = c))); showAddTicketDialog = false },
            { showAddTicketDialog = false })
    }
    editingTicket?.let { t ->
        PriceEditDialog("Edit Ticket", t.name, if (t.price > 0) t.price.toString() else "", currencies.value, t.currency,
            { n, p, c -> tripViewModel.updateTrip(trip.copy(planeTickets = trip.planeTickets.map { if (it.id == t.id) it.copy(name = n, price = p, currency = c) else it })); editingTicket = null },
            { editingTicket = null })
    }
    ticketToDelete?.let { t ->
        DeleteConfirmDialog("Delete Ticket", t.name, { tripViewModel.updateTrip(trip.copy(planeTickets = trip.planeTickets.filter { it.id != t.id })); ticketToDelete = null }, { ticketToDelete = null })
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
                ) { Text("Set API Key", color = DraculaGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showMissingKeyDialog = false }) { Text("Cancel") }
            },
            containerColor = DraculaCurrent,
            titleContentColor = DraculaForeground,
            textContentColor = DraculaForeground,
        )
    }
}

// ════════════════ REUSABLE COMPOSABLES ════════════════

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = DraculaPurple,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
}

@Composable
private fun draculaTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DraculaPurple, focusedLabelColor = DraculaPurple, cursorColor = DraculaPurple,
)


@Composable
private fun PriceItemCard(name: String, price: Double, currency: String, badge: String? = null, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).clickable { onClick() },
        shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = DraculaCurrent),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name.ifBlank { "(No name)" }, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DraculaForeground)
                    if (badge != null) { Spacer(Modifier.width(6.dp)); Text(badge, fontSize = 10.sp, color = DraculaCyan) }
                }
                Text(if (price > 0) "💰 $price $currency" else "Price not set", fontSize = 13.sp,
                    color = if (price > 0) DraculaGreen else DraculaComment)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Delete", tint = DraculaRed, modifier = Modifier.size(18.dp))
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
    var name by remember { mutableStateOf(initialName) }
    var priceText by remember { mutableStateOf(initialPrice) }
    var currency by remember { mutableStateOf(initialCurrency) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = draculaTextFieldColors())
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = priceText, onValueChange = { priceText = it }, label = { Text("Price") },
                        placeholder = { Text("0.00") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), colors = draculaTextFieldColors())
                    CurrencyPicker(selected = currency, currencies = currencies, onSelect = { currency = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim(), priceText.toDoubleOrNull() ?: 0.0, currency) }) { Text("OK", color = DraculaGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = DraculaCurrent, titleContentColor = DraculaForeground, textContentColor = DraculaForeground,
    )
}

@Composable
private fun DeleteConfirmDialog(title: String, itemName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { Text("Delete \"${itemName.ifBlank { "(unnamed)" }}\"?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = DraculaRed) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = DraculaCurrent, titleContentColor = DraculaForeground, textContentColor = DraculaForeground,
    )
}
