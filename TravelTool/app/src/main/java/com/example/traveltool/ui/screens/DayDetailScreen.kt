package com.example.traveltool.ui.screens

import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.data.Accommodation
import com.example.traveltool.data.SunriseSunsetApi
import com.example.traveltool.data.TripViewModel
import com.example.traveltool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

/**
 * Day detail page — shows sunrise/sunset, moving/staying day, and location info
 * for a single day of the trip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    tripId: String,
    dayMillis: Long,
    tripViewModel: TripViewModel,
    onBack: () -> Unit
) {
    val trip = tripViewModel.getTripById(tripId)
    if (trip == null) { onBack(); return }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }
    val dayLabel = remember(dayMillis) { dateFormat.format(Date(dayMillis)) }

    // Determine which day number this is (1-based)
    val dayNumber = remember(dayMillis, trip.startMillis) {
        ((dayMillis - trip.startMillis) / ONE_DAY_MS).toInt() + 1
    }
    val totalDays = remember(trip.startMillis, trip.endMillis) {
        ((trip.endMillis - trip.startMillis) / ONE_DAY_MS).toInt()
    }

    // Find the accommodation covering this day
    val todayAccom = remember(trip.accommodations, dayMillis) {
        findAccommodationForDay(trip.accommodations, dayMillis)
    }

    // Find the accommodation covering the previous day (for moving/staying detection)
    val prevDayAccom = remember(trip.accommodations, dayMillis) {
        if (dayMillis > trip.startMillis) {
            findAccommodationForDay(trip.accommodations, dayMillis - ONE_DAY_MS)
        } else null
    }

    // Find the accommodation covering the next day
    val nextDayAccom = remember(trip.accommodations, dayMillis) {
        findAccommodationForDay(trip.accommodations, dayMillis + ONE_DAY_MS)
    }

    // Determine if this is a moving day or staying day
    val isMovingDay = remember(todayAccom, prevDayAccom, dayMillis, trip.startMillis) {
        when {
            // First day of trip → always moving (traveling from home)
            dayMillis == trip.startMillis -> true
            // No accommodation today → can't determine
            todayAccom == null -> false
            // Previous day had different accommodation (different location) → moving day
            prevDayAccom == null -> true
            prevDayAccom.location != todayAccom.location -> true
            else -> false
        }
    }

    // Determine the location label for today
    val locationLabel = todayAccom?.location?.ifBlank { trip.location } ?: trip.location

    // Sunrise / sunset state
    var sunTimes by remember { mutableStateOf<SunriseSunsetApi.SunTimes?>(null) }
    var sunTimesLoading by remember { mutableStateOf(true) }
    var sunTimesError by remember { mutableStateOf<String?>(null) }

    // Fetch sunrise/sunset on launch
    LaunchedEffect(dayMillis, locationLabel) {
        sunTimesLoading = true
        sunTimesError = null
        scope.launch {
            try {
                // Geocode the location to get lat/lng
                val coords = withContext(Dispatchers.IO) {
                    try {
                        @Suppress("DEPRECATION")
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val addresses = geocoder.getFromLocationName(locationLabel, 1)
                        if (!addresses.isNullOrEmpty()) {
                            Pair(addresses[0].latitude, addresses[0].longitude)
                        } else null
                    } catch (_: Exception) { null }
                }

                if (coords != null) {
                    val result = SunriseSunsetApi.getSunTimes(coords.first, coords.second, dayMillis)
                    if (result != null) {
                        sunTimes = result
                    } else {
                        sunTimesError = "Could not fetch sun times"
                    }
                } else {
                    sunTimesError = "Could not geocode location"
                }
            } catch (e: Exception) {
                sunTimesError = e.message ?: "Unknown error"
            } finally {
                sunTimesLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Day $dayNumber") },
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
            // ── Date header ──
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    text = dayLabel,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DraculaForeground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Day $dayNumber of $totalDays  •  📍 $locationLabel",
                    fontSize = 14.sp,
                    color = DraculaComment,
                )
            }

            HorizontalDivider(color = DraculaCurrent)

            // ── Moving / Staying day ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMovingDay) DraculaOrange.copy(alpha = 0.15f)
                    else DraculaGreen.copy(alpha = 0.15f)
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isMovingDay) "🚗" else "🏨",
                        fontSize = 28.sp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isMovingDay) "Moving Day" else "Staying Day",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMovingDay) DraculaOrange else DraculaGreen,
                        )
                        Text(
                            text = if (isMovingDay) {
                                when {
                                    dayMillis == trip.startMillis ->
                                        "Traveling from ${trip.startingPoint.ifBlank { "home" }} to $locationLabel"
                                    prevDayAccom != null ->
                                        "Moving from ${prevDayAccom.location} to $locationLabel"
                                    else ->
                                        "Traveling to a new location"
                                }
                            } else {
                                "Staying at ${todayAccom?.name?.ifBlank { locationLabel } ?: locationLabel}"
                            },
                            fontSize = 13.sp,
                            color = DraculaComment,
                        )
                    }
                }
            }

            // ── Accommodation info ──
            if (todayAccom != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DraculaCurrent),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Accommodation", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DraculaPurple)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = todayAccom.name.ifBlank { "(Not named)" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DraculaForeground,
                        )
                        if (todayAccom.location.isNotBlank()) {
                            Text("📍 ${todayAccom.location}", fontSize = 13.sp, color = DraculaComment)
                        }
                        if (todayAccom.pricePerNight != null && todayAccom.pricePerNight > 0) {
                            Text(
                                "💰 ${todayAccom.pricePerNight} ${todayAccom.priceCurrency} / night",
                                fontSize = 13.sp,
                                color = DraculaGreen,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Sunrise / Sunset ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DraculaCurrent),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sunrise & Sunset", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DraculaPurple)
                    Spacer(Modifier.height(12.dp))

                    if (sunTimesLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = DraculaPurple,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Fetching sun times…", fontSize = 14.sp, color = DraculaComment)
                        }
                    } else if (sunTimesError != null) {
                        Text("⚠️ $sunTimesError", fontSize = 14.sp, color = DraculaYellow)
                    } else if (sunTimes != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            // Sunrise
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🌅", fontSize = 32.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Sunrise", fontSize = 12.sp, color = DraculaComment)
                                Text(
                                    sunTimes!!.sunriseFormatted,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DraculaOrange,
                                )
                            }

                            // Day length
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("☀️", fontSize = 32.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Daylight", fontSize = 12.sp, color = DraculaComment)
                                val hours = sunTimes!!.dayLengthSeconds / 3600
                                val minutes = (sunTimes!!.dayLengthSeconds % 3600) / 60
                                Text(
                                    "${hours}h ${minutes}m",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DraculaYellow,
                                )
                            }

                            // Sunset
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🌇", fontSize = 32.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Sunset", fontSize = 12.sp, color = DraculaComment)
                                Text(
                                    sunTimes!!.sunsetFormatted,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DraculaPink,
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Times are estimated for the location's timezone",
                            fontSize = 11.sp,
                            color = DraculaComment,
                        )
                    }
                }
            }

            // ── What's next section ──
            if (nextDayAccom != null && todayAccom != null &&
                nextDayAccom.location != todayAccom.location &&
                nextDayAccom.location.isNotBlank()
            ) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DraculaCyan.copy(alpha = 0.10f)
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Tomorrow", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DraculaCyan)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "🚗 Moving to ${nextDayAccom.location}",
                            fontSize = 14.sp,
                            color = DraculaForeground,
                        )
                        if (nextDayAccom.name.isNotBlank()) {
                            Text(
                                text = "🏨 ${nextDayAccom.name}",
                                fontSize = 13.sp,
                                color = DraculaComment,
                            )
                        }
                    }
                }
            }

            // Check if this is the last day → going home
            val isLastDay = dayMillis + ONE_DAY_MS >= trip.endMillis
            if (isLastDay && trip.startingPoint.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DraculaCyan.copy(alpha = 0.10f)
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Tomorrow", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DraculaCyan)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "🏠 Returning home to ${trip.startingPoint}",
                            fontSize = 14.sp,
                            color = DraculaForeground,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Find the accommodation whose date range covers the given day.
 * Uses: accom.startMillis <= dayMillis < accom.endMillis
 * (if accom.endMillis == dayMillis, the user is checking out that day)
 */
private fun findAccommodationForDay(
    accommodations: List<Accommodation>,
    dayMillis: Long
): Accommodation? {
    // First try strict range
    val strict = accommodations.find { it.startMillis <= dayMillis && dayMillis < it.endMillis }
    if (strict != null) return strict

    // If it's the exact endMillis of an accommodation (last day), include it
    return accommodations.find { it.startMillis <= dayMillis && dayMillis <= it.endMillis }
}
