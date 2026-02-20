package com.example.traveltool.ui.screens

import android.location.Geocoder
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.data.Accommodation
import com.example.traveltool.data.CurrencyManager
import com.example.traveltool.data.TripViewModel
import com.example.traveltool.ui.components.CurrencyPicker
import com.example.traveltool.ui.theme.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Add a new accommodation to a trip.
 *
 * Dates already occupied by existing accommodations are blocked from selection,
 * except boundary days (start/end of existing accommodations) which can be
 * used for chaining accommodations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccommodationScreen(
    tripId: String,
    tripViewModel: TripViewModel,
    onBack: () -> Unit
) {
    val trip = tripViewModel.getTripById(tripId)

    if (trip == null) {
        onBack()
        return
    }

    val oneDayMs = 24 * 60 * 60 * 1000L

    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var priceCurrency by remember { mutableStateOf(trip.displayCurrency) }
    var accomLocation by remember { mutableStateOf("") }
    var markerPosition by remember { mutableStateOf<LatLng?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(48.8566, 2.3522), 5f)
    }

    fun searchLocation() {
        if (accomLocation.isBlank()) return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocationName(accomLocation.trim(), 1)
                    if (!addresses.isNullOrEmpty()) {
                        LatLng(addresses[0].latitude, addresses[0].longitude)
                    } else null
                } catch (_: Exception) { null }
            }
            if (result != null) {
                markerPosition = result
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(result, 12f))
            } else {
                Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Compute which days are occupied by any existing accommodation (all blocked)
    val existingAccommodations = trip.accommodations
    val occupiedDays = remember(existingAccommodations) {
        val set = mutableSetOf<Long>()
        for (accom in existingAccommodations) {
            var d = accom.startMillis
            while (d <= accom.endMillis) {
                set.add(normalizeToDay(d))
                d += oneDayMs
            }
        }
        set
    }

    // Trip range normalized for visual highlighting
    val tripStartDay = remember(trip.startMillis) { normalizeToDay(trip.startMillis) }
    val tripEndDay = remember(trip.endMillis) { normalizeToDay(trip.endMillis) }

    val selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val normalized = normalizeToDay(utcTimeMillis)
            // Must be within trip range
            if (normalized < tripStartDay || normalized > tripEndDay) return false
            // All occupied days are blocked
            if (normalized in occupiedDays) return false
            return true
        }
    }

    val dateRangePickerState = rememberDateRangePickerState(
        initialDisplayMode = DisplayMode.Picker,
        selectableDates = selectableDates,
        initialDisplayedMonthMillis = trip.startMillis,
    )

    val startMillis = dateRangePickerState.selectedStartDateMillis
    val endMillis   = dateRangePickerState.selectedEndDateMillis
    val rangeReady  = startMillis != null && endMillis != null

    val canSave = name.isNotBlank() && rangeReady

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Accommodation") },
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
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = {
                            val price = priceText.toDoubleOrNull()
                            val accom = Accommodation(
                                name = name.trim(),
                                startMillis = startMillis!!,
                                endMillis = endMillis!!,
                                pricePerNight = price,
                                priceCurrency = priceCurrency,
                                location = accomLocation.trim()
                            )
                            tripViewModel.addAccommodation(tripId, accom)
                            onBack()
                        },
                        enabled = canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DraculaGreen,
                            contentColor = MaterialTheme.colorScheme.background,
                        )
                    ) {
                        Text("Save Accommodation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // --- Name input ---
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Accommodation name") },
                placeholder = { Text("e.g. Hotel Ritz") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DraculaPurple,
                    focusedLabelColor = DraculaPurple,
                    cursorColor = DraculaPurple,
                )
            )

            Spacer(Modifier.height(12.dp))

            // --- Price per night input ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Price per night") },
                    placeholder = { Text("e.g. 120.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DraculaPurple,
                        focusedLabelColor = DraculaPurple,
                        cursorColor = DraculaPurple,
                    )
                )
                CurrencyPicker(
                    selected = priceCurrency,
                    currencies = CurrencyManager.getCurrencyList(context),
                    onSelect = { priceCurrency = it },
                )
            }

            Spacer(Modifier.height(12.dp))

            // --- Location input with search ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = accomLocation,
                    onValueChange = { accomLocation = it },
                    label = { Text("Location") },
                    placeholder = { Text("e.g. Paris, France") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DraculaPurple,
                        focusedLabelColor = DraculaPurple,
                        cursorColor = DraculaPurple,
                    )
                )
                FilledIconButton(
                    onClick = { searchLocation() },
                    enabled = accomLocation.isNotBlank(),
                    modifier = Modifier.height(56.dp).width(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = DraculaPurple,
                        contentColor = DraculaForeground,
                    )
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search location")
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Mini map ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                ) {
                    markerPosition?.let { pos ->
                        Marker(
                            state = MarkerState(position = pos),
                            title = accomLocation.ifBlank { "Location" },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Date selection ---
            Text(
                text = "Select dates (greyed-out dates are occupied or outside trip)",
                fontSize = 14.sp,
                color = DraculaComment,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp),
                showModeToggle = false,
                title = null,
                headline = {
                    Text(
                        text = if (rangeReady) "Dates selected ✓"
                               else if (startMillis != null) "Now pick the end date"
                               else "Tap the start date",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background,
                    selectedDayContainerColor = DraculaPurple,
                    todayDateBorderColor = DraculaGreen,
                    dayInSelectionRangeContainerColor = DraculaPurple.copy(alpha = .25f),
                    todayContentColor = DraculaGreen,
                    dayContentColor = DraculaGreen,
                ),
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Normalize a timestamp to the start of its UTC day. */
private fun normalizeToDay(millis: Long): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
