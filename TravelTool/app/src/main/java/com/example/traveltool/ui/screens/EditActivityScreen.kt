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
import androidx.compose.material.icons.filled.Delete
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
import java.util.*

/**
 * Screen to edit an existing activity.
 * Allows changing name, location, duration, category, and description.
 * Recalculates driving time when the location changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditActivityScreen(
    tripId: String,
    activityId: String,
    tripViewModel: TripViewModel,
    onBack: () -> Unit,
) {
    val trip = tripViewModel.getTripById(tripId)
    val activity = trip?.activities?.find { it.id == activityId }
    if (trip == null || activity == null) { onBack(); return }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(activity.name) }
    var location by remember { mutableStateOf(activity.location) }
    var durationText by remember { mutableStateOf(activity.durationMinutes.toString()) }
    var description by remember { mutableStateOf(activity.description) }
    var selectedCategory by remember { mutableStateOf(activity.category) }
    var selectedEatingType by remember { mutableStateOf(activity.eatingType) }
    var markerPosition by remember { mutableStateOf<LatLng?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Track whether location changed (to recalculate driving time)
    val locationChanged = location.trim() != activity.location.trim()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(48.8566, 2.3522), 5f)
    }

    // Geocode original location on mount
    LaunchedEffect(activity.location) {
        if (activity.location.isNotBlank()) {
            val result = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocationName(activity.location.trim(), 1)
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

    // Determine "from" location (previous activity or accommodation)
    val existingActivities = tripViewModel.getActivitiesForDay(tripId, activity.dayMillis)
    val prevActivity = existingActivities.filter { it.orderIndex < activity.orderIndex }.maxByOrNull { it.orderIndex }
    val todayAccom = trip.accommodations.find {
        it.startMillis <= activity.dayMillis && activity.dayMillis < it.endMillis
    } ?: trip.accommodations.find {
        it.startMillis <= activity.dayMillis && activity.dayMillis <= it.endMillis
    }
    val fromLocation = prevActivity?.location
        ?: todayAccom?.location?.ifBlank { trip.startingPoint }
        ?: trip.startingPoint

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

    val canSave = name.isNotBlank() && location.isNotBlank() && !isSaving
    val durationMinutes = durationText.toIntOrNull() ?: 60

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Activity") },
            text = { Text("Are you sure you want to delete \"${activity.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        tripViewModel.deleteActivity(tripId, activityId)
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = DraculaRed),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Activity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = DraculaRed,
                        )
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
                            scope.launch {
                                // Recalculate driving time if location changed
                                val drivingTo = if (locationChanged && apiKey.isNotBlank() && fromLocation.isNotBlank()) {
                                    DirectionsApiHelper.estimateDrivingTime(
                                        from = fromLocation,
                                        to = location.trim(),
                                        openAiApiKey = apiKey,
                                        model = model,
                                    )
                                } else {
                                    activity.drivingTimeToMinutes
                                }

                                val updated = activity.copy(
                                    name = name.trim(),
                                    location = location.trim(),
                                    durationMinutes = durationMinutes,
                                    drivingTimeToMinutes = drivingTo,
                                    description = description.trim(),
                                    category = selectedCategory,
                                    eatingType = selectedEatingType,
                                )
                                tripViewModel.updateActivity(tripId, updated)
                                isSaving = false
                                onBack()
                            }
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
                            Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
            Spacer(Modifier.height(12.dp))

            // ── Category selection ──
            Text(
                text = "Category",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = DraculaForeground,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActivityCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat.name,
                        onClick = {
                            selectedCategory = if (selectedCategory == cat.name) "" else cat.name
                            if (selectedCategory != ActivityCategory.EATING.name) selectedEatingType = ""
                        },
                        label = { Text("${cat.emoji} ${cat.label}", fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DraculaPurple.copy(alpha = 0.3f),
                            selectedLabelColor = DraculaForeground,
                        ),
                    )
                }
            }

            // Eating sub-selection
            if (selectedCategory == ActivityCategory.EATING.name) {
                Spacer(Modifier.height(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EatingType.entries.forEach { et ->
                        FilterChip(
                            selected = selectedEatingType == et.name,
                            onClick = {
                                selectedEatingType = if (selectedEatingType == et.name) "" else et.name
                            },
                            label = { Text(et.label, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DraculaCyan.copy(alpha = 0.3f),
                                selectedLabelColor = DraculaForeground,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Activity name ---
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Activity name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DraculaPurple,
                    focusedLabelColor = DraculaPurple,
                    cursorColor = DraculaPurple,
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
                        focusedBorderColor = DraculaPurple,
                        focusedLabelColor = DraculaPurple,
                        cursorColor = DraculaPurple,
                    )
                )
                FilledIconButton(
                    onClick = { searchLocation() },
                    enabled = location.isNotBlank(),
                    modifier = Modifier.height(56.dp).width(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = DraculaPurple,
                        contentColor = DraculaForeground,
                    )
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search location")
                }
            }

            if (locationChanged) {
                Text(
                    text = "Location changed — driving time will be re-estimated on save.",
                    fontSize = 11.sp,
                    color = DraculaOrange,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
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
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DraculaPurple,
                    focusedLabelColor = DraculaPurple,
                    cursorColor = DraculaPurple,
                )
            )

            Spacer(Modifier.height(16.dp))

            // --- Description ---
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DraculaPurple,
                    focusedLabelColor = DraculaPurple,
                    cursorColor = DraculaPurple,
                )
            )

            Spacer(Modifier.height(16.dp))

            // --- Driving time info ---
            if (activity.drivingTimeToMinutes != null && !locationChanged) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DraculaCurrent),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Current driving time", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DraculaPurple)
                        Spacer(Modifier.height(8.dp))
                        val h = activity.drivingTimeToMinutes / 60
                        val m = activity.drivingTimeToMinutes % 60
                        Text(
                            text = "\uD83D\uDE97 ${if (h > 0) "${h}h " else ""}${m}min from ${fromLocation.take(30)}${if (fromLocation.length > 30) "\u2026" else ""}",
                            fontSize = 13.sp,
                            color = DraculaGreen,
                        )
                    }
                }
            }

            // --- Rating info ---
            if (activity.rating != null) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DraculaCurrent),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⭐", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Rating: ${String.format(Locale.US, "%.1f", activity.rating)}",
                            fontSize = 14.sp,
                            color = DraculaYellow,
                        )
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}
