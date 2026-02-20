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
import com.example.traveltool.data.*
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

private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

/**
 * Screen to add a new activity for a specific trip day.
 * Departure time is set per-day on DayDetailScreen; activities chain sequentially.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddActivityScreen(
    tripId: String,
    dayMillis: Long,
    tripViewModel: TripViewModel,
    onRecommend: () -> Unit,
    onBack: () -> Unit,
    prefillName: String? = null,
    prefillLocation: String? = null,
    prefillDescription: String? = null,
    prefillRating: Double? = null,
    prefillCategory: String? = null,
    prefillEatingType: String? = null,
) {
    val colors = LocalAppColors.current
    val trip = tripViewModel.getTripById(tripId)
    if (trip == null) { onBack(); return }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val dayLabel = remember(dayMillis) { dateFormat.format(Date(dayMillis)) }

    var name by remember { mutableStateOf(prefillName ?: "") }
    var location by remember { mutableStateOf(prefillLocation ?: "") }
    var durationText by remember { mutableStateOf("60") }
    var description by remember { mutableStateOf(prefillDescription ?: "") }
    var rating by remember { mutableStateOf(prefillRating) }
    var selectedCategory by remember { mutableStateOf(prefillCategory ?: "") }
    var selectedEatingType by remember { mutableStateOf(prefillEatingType ?: "") }
    var markerPosition by remember { mutableStateOf<LatLng?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(48.8566, 2.3522), 5f)
    }

    // Find accommodation covering this day
    val todayAccom = remember(trip.accommodations, dayMillis) {
        trip.accommodations.find { it.startMillis <= dayMillis && dayMillis < it.endMillis }
            ?: trip.accommodations.find { it.startMillis <= dayMillis && dayMillis <= it.endMillis }
    }

    // Previous day accommodation (for moving day detection)
    val prevDayAccom = remember(trip.accommodations, dayMillis) {
        if (dayMillis > trip.startMillis) {
            val prevDay = dayMillis - ONE_DAY_MS
            trip.accommodations.find { it.startMillis <= prevDay && prevDay < it.endMillis }
                ?: trip.accommodations.find { it.startMillis <= prevDay && prevDay <= it.endMillis }
        } else null
    }

    val isFinalDay = dayMillis >= trip.endMillis
    val isMovingDay = remember(todayAccom, prevDayAccom, dayMillis, trip.startMillis, trip.endMillis) {
        when {
            dayMillis == trip.startMillis -> true
            isFinalDay -> true
            todayAccom == null -> false
            prevDayAccom == null -> true
            prevDayAccom.location != todayAccom.location -> true
            else -> false
        }
    }

    // Travel day position: before or after arrival
    var selectedPosition by remember { mutableStateOf(TravelDayPosition.AFTER_ARRIVAL.name) }

    // Origin location for this travel day
    val originLocation = when {
        dayMillis == trip.startMillis -> trip.startingPoint
        prevDayAccom != null -> prevDayAccom.location
        todayAccom != null -> todayAccom.location
        else -> trip.startingPoint
    }

    // Destination for this travel day
    val destLocation = when {
        isFinalDay -> trip.endingPoint.ifBlank { trip.startingPoint }
        todayAccom != null -> todayAccom.location
        else -> trip.startingPoint
    }

    // Get existing activities for this day to determine "from" location
    val existingActivities = tripViewModel.getActivitiesForDay(tripId, dayMillis)
    val fromLocation = if (isMovingDay) {
        if (selectedPosition == TravelDayPosition.BEFORE_ARRIVAL.name) {
            val sameGroup = existingActivities.filter { it.travelDayPosition == TravelDayPosition.BEFORE_ARRIVAL.name }
            sameGroup.maxByOrNull { it.orderIndex }?.location ?: originLocation
        } else {
            val sameGroup = existingActivities.filter { it.travelDayPosition != TravelDayPosition.BEFORE_ARRIVAL.name }
            sameGroup.maxByOrNull { it.orderIndex }?.location ?: destLocation
        }
    } else {
        if (existingActivities.isNotEmpty()) {
            existingActivities.last().location
        } else {
            todayAccom?.location?.ifBlank { trip.startingPoint } ?: trip.startingPoint
        }
    }

    fun searchLocation() {
        if (location.isBlank()) return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocationName(location.trim(), 1)
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

    // Auto-search prefilled location
    LaunchedEffect(prefillLocation) {
        if (!prefillLocation.isNullOrBlank()) {
            val result = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocationName(prefillLocation.trim(), 1)
                    if (!addresses.isNullOrEmpty()) {
                        LatLng(addresses[0].latitude, addresses[0].longitude)
                    } else null
                } catch (_: Exception) { null }
            }
            if (result != null) {
                markerPosition = result
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(result, 12f))
            }
        }
    }

    val canSave = name.isNotBlank() && location.isNotBlank() && !isSaving
    val durationMinutes = durationText.toIntOrNull() ?: 60

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Activity") },
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
                            isSaving = true
                            val apiKey = ApiKeyStore.getOpenAiKey(context)
                            val model = ApiKeyStore.getOpenAiModel(context)
                            val googleKey = try {
                                val appInfo = context.packageManager.getApplicationInfo(
                                    context.packageName,
                                    android.content.pm.PackageManager.GET_META_DATA
                                )
                                appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
                            } catch (_: Exception) { "" }
                            scope.launch {
                                // Estimate driving time from previous location to this activity
                                var drivingTo: Int? = null
                                var drivingDistTo: Double? = null
                                var returnTime: Int? = null
                                var returnDist: Double? = null

                                if (fromLocation.isNotBlank()) {
                                    val est = DirectionsApiHelper.estimateDrivingTime(
                                        from = fromLocation,
                                        to = location.trim(),
                                        googleApiKey = googleKey,
                                        openAiApiKey = apiKey,
                                        model = model,
                                    )
                                    drivingTo = est?.timeMinutes
                                    drivingDistTo = est?.distanceKm
                                }

                                // Estimate return drive from this activity back to destination/accommodation
                                val returnTo = if (isFinalDay) {
                                    trip.endingPoint.ifBlank { trip.startingPoint.ifBlank { null } }
                                } else {
                                    todayAccom?.location?.ifBlank { null }
                                        ?: trip.startingPoint.ifBlank { null }
                                }
                                if (apiKey.isNotBlank() && returnTo != null) {
                                    val retEst = DirectionsApiHelper.estimateDrivingTime(
                                        from = location.trim(),
                                        to = returnTo,
                                        googleApiKey = googleKey,
                                        openAiApiKey = apiKey,
                                        model = model,
                                    )
                                    returnTime = retEst?.timeMinutes
                                    returnDist = retEst?.distanceKm
                                }

                                // Clear return drive on previous last activity in the same group
                                if (isMovingDay) {
                                    val sameGroup = existingActivities.filter {
                                        if (selectedPosition == TravelDayPosition.BEFORE_ARRIVAL.name)
                                            it.travelDayPosition == TravelDayPosition.BEFORE_ARRIVAL.name
                                        else
                                            it.travelDayPosition != TravelDayPosition.BEFORE_ARRIVAL.name
                                    }
                                    val prev = sameGroup.maxByOrNull { it.orderIndex }
                                    if (prev != null) {
                                        tripViewModel.updateActivity(
                                            tripId,
                                            prev.copy(returnDrivingTimeMinutes = null, returnDrivingDistanceKm = null)
                                        )
                                    }
                                } else if (existingActivities.isNotEmpty()) {
                                    val prev = existingActivities.last()
                                    tripViewModel.updateActivity(
                                        tripId,
                                        prev.copy(returnDrivingTimeMinutes = null, returnDrivingDistanceKm = null)
                                    )
                                }

                                val activity = Activity(
                                    dayMillis = dayMillis,
                                    name = name.trim(),
                                    location = location.trim(),
                                    durationMinutes = durationMinutes,
                                    drivingTimeToMinutes = drivingTo,
                                    drivingDistanceToKm = drivingDistTo,
                                    returnDrivingTimeMinutes = returnTime,
                                    returnDrivingDistanceKm = returnDist,
                                    description = description.trim(),
                                    rating = rating,
                                    category = selectedCategory,
                                    eatingType = selectedEatingType,
                                    travelDayPosition = if (isMovingDay) selectedPosition else "",
                                )
                                tripViewModel.addActivity(tripId, activity)
                                isSaving = false
                                onBack()
                            }
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
                        if (isSaving) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.background,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("Estimating travel time\u2026", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        } else {
                            Text("Save Activity", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
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
            // Day label
            Text(
                text = "Activity for $dayLabel",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colors.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            // ── Travel day position picker (only on moving days) ──
            if (isMovingDay) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedPosition == TravelDayPosition.BEFORE_ARRIVAL.name,
                        onClick = { selectedPosition = TravelDayPosition.BEFORE_ARRIVAL.name },
                        label = { Text("\uD83D\uDEE3\uFE0F On the way") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.orange.copy(alpha = 0.2f),
                            selectedLabelColor = colors.orange,
                        ),
                    )
                    FilterChip(
                        selected = selectedPosition == TravelDayPosition.AFTER_ARRIVAL.name,
                        onClick = { selectedPosition = TravelDayPosition.AFTER_ARRIVAL.name },
                        label = { Text("\uD83C\uDFE8 After arrival") },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.green.copy(alpha = 0.2f),
                            selectedLabelColor = colors.green,
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Recommend something button ──
            OutlinedButton(
                onClick = onRecommend,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.accent,
                ),
            ) {
                Text("\uD83D\uDCA1 Recommend something", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(16.dp))

            // --- Activity name ---
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Activity name") },
                placeholder = { Text("e.g. Visit Eiffel Tower") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    focusedLabelColor = colors.primary,
                    cursorColor = colors.primary,
                )
            )

            Spacer(Modifier.height(12.dp))

            // --- Location with search ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    placeholder = { Text("e.g. Eiffel Tower, Paris") },
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
                    enabled = location.isNotBlank(),
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
                    .height(160.dp)
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
                            title = location.ifBlank { "Location" },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Duration ---
            OutlinedTextField(
                value = durationText,
                onValueChange = { durationText = it },
                label = { Text("Time at activity (minutes)") },
                placeholder = { Text("e.g. 120") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    focusedLabelColor = colors.primary,
                    cursorColor = colors.primary,
                )
            )

            Spacer(Modifier.height(16.dp))

            // --- Description (optional) ---
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                placeholder = { Text("Brief description") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    focusedLabelColor = colors.primary,
                    cursorColor = colors.primary,
                )
            )

            Spacer(Modifier.height(16.dp))

            // --- Route summary info ---
            if (fromLocation.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.current),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Route info", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "\uD83D\uDCCD From: $fromLocation",
                            fontSize = 13.sp,
                            color = colors.comment,
                        )
                        if (location.isNotBlank()) {
                            Text(
                                text = "\uD83D\uDCCD To: ${location.trim()}",
                                fontSize = 13.sp,
                                color = colors.green,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Driving time will be estimated by AI after saving.",
                            fontSize = 11.sp,
                            color = colors.comment,
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}
