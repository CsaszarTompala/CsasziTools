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
 * Edit an existing accommodation — change name, price, location, dates.
 *
 * Surrounding accommodations are adjusted automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAccommodationScreen(
    tripId: String,
    accomId: String,
    tripViewModel: TripViewModel,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val trip = tripViewModel.getTripById(tripId)
    val accom = trip?.accommodations?.find { it.id == accomId }

    if (trip == null || accom == null) {
        onBack()
        return
    }

    var name by remember { mutableStateOf(accom.name) }
    var priceText by remember { mutableStateOf(accom.pricePerNight?.toString() ?: "") }
    var priceCurrency by remember { mutableStateOf(accom.priceCurrency) }
    var accomLocation by remember { mutableStateOf(accom.location) }
    var markerPosition by remember { mutableStateOf<LatLng?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(48.8566, 2.3522), 5f)
    }

    // Pre-search current accommodation location on first composition
    LaunchedEffect(Unit) {
        val loc = accomLocation.ifBlank { trip.location }
        if (loc.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocationName(loc.trim(), 1)
                    if (!addresses.isNullOrEmpty()) {
                        val pos = LatLng(addresses[0].latitude, addresses[0].longitude)
                        markerPosition = pos
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngZoom(pos, 12f)
                        )
                    }
                } catch (_: Exception) { }
            }
        }
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

    val selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            return utcTimeMillis >= trip.startMillis && utcTimeMillis <= trip.endMillis
        }
    }

    val dateRangePickerState = rememberDateRangePickerState(
        initialDisplayMode = DisplayMode.Picker,
        selectableDates = selectableDates,
        initialSelectedStartDateMillis = accom.startMillis,
        initialSelectedEndDateMillis = accom.endMillis,
        initialDisplayedMonthMillis = trip.startMillis,
    )

    val startMillis = dateRangePickerState.selectedStartDateMillis
    val endMillis   = dateRangePickerState.selectedEndDateMillis
    val rangeReady  = startMillis != null && endMillis != null

    val canSave = name.isNotBlank() && rangeReady

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Accommodation") },
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
                            val updated = accom.copy(
                                name = name.trim(),
                                startMillis = startMillis!!,
                                endMillis = endMillis!!,
                                pricePerNight = price,
                                priceCurrency = priceCurrency,
                                location = accomLocation.trim()
                            )
                            tripViewModel.updateAccommodation(tripId, updated)
                            onBack()
                        },
                        enabled = canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.green,
                            contentColor = MaterialTheme.colorScheme.background,
                        )
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                    focusedBorderColor = colors.primary,
                    focusedLabelColor = colors.primary,
                    cursorColor = colors.primary,
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
                        focusedBorderColor = colors.primary,
                        focusedLabelColor = colors.primary,
                        cursorColor = colors.primary,
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
                        focusedBorderColor = colors.primary,
                        focusedLabelColor = colors.primary,
                        cursorColor = colors.primary,
                    )
                )
                FilledIconButton(
                    onClick = { searchLocation() },
                    enabled = accomLocation.isNotBlank(),
                    modifier = Modifier.height(56.dp).width(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.primary,
                        contentColor = colors.foreground,
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

            // --- Date range picker ---
            Text(
                text = "Select dates (only trip dates available)",
                fontSize = 14.sp,
                color = colors.comment,
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
                    selectedDayContainerColor = colors.primary,
                    todayDateBorderColor = colors.primary,
                    dayInSelectionRangeContainerColor = colors.primary.copy(alpha = .25f),
                    todayContentColor = colors.primary,
                ),
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

