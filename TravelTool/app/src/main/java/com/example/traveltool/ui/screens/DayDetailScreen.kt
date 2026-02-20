package com.example.traveltool.ui.screens

import android.location.Geocoder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.data.Accommodation
import com.example.traveltool.data.Activity
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
 * Day detail page — shows sunrise/sunset, moving/staying day, accommodation info,
 * departure time picker, and sequential activity timeline for a single trip day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    tripId: String,
    dayMillis: Long,
    tripViewModel: TripViewModel,
    onAddActivity: () -> Unit,
    onEditActivity: (String) -> Unit,
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

    // ── Day plan (departure time) ──
    val dayPlan = tripViewModel.getDayPlan(tripId, dayMillis)
    val timePickerState = rememberTimePickerState(
        initialHour = dayPlan.departureHour,
        initialMinute = dayPlan.departureMinute,
        is24Hour = true,
    )

    // Save departure time when it changes
    LaunchedEffect(timePickerState.hour, timePickerState.minute) {
        if (timePickerState.hour != dayPlan.departureHour || timePickerState.minute != dayPlan.departureMinute) {
            tripViewModel.setDayPlan(
                tripId,
                dayPlan.copy(
                    dayMillis = dayMillis,
                    departureHour = timePickerState.hour,
                    departureMinute = timePickerState.minute,
                )
            )
        }
    }

    val isFinalDay = dayMillis >= trip.endMillis
    val isLastDay = dayMillis + ONE_DAY_MS >= trip.endMillis

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

            // Check if this is the last day
            val isFinalDay = dayMillis >= trip.endMillis
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

            // ── Activities section ──
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = DraculaCurrent)
            Spacer(Modifier.height(12.dp))

            val dayActivities = tripViewModel.getActivitiesForDay(tripId, dayMillis)

            Text(
                text = "Activities",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DraculaForeground,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            // ── Departure time picker ──
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DraculaCurrent),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Departure time",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = DraculaPurple,
                    )
                    Text(
                        text = "When you leave ${todayAccom?.name?.ifBlank { "your accommodation" } ?: "your accommodation"} for the day",
                        fontSize = 12.sp,
                        color = DraculaComment,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    TimeInput(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            timeSelectorSelectedContainerColor = DraculaPurple.copy(alpha = 0.25f),
                            timeSelectorSelectedContentColor = DraculaForeground,
                            timeSelectorUnselectedContainerColor = DraculaCurrent,
                            timeSelectorUnselectedContentColor = DraculaForeground,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (dayActivities.isEmpty()) {
                Text(
                    text = "No activities planned for this day",
                    fontSize = 13.sp,
                    color = DraculaComment,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else {
                // ── Sequential activity timeline ──
                var cumulativeMinutes = timePickerState.hour * 60 + timePickerState.minute

                dayActivities.forEachIndexed { index, activity ->
                    val driveTo = activity.drivingTimeToMinutes ?: 0
                    val fromLabel = if (index == 0) {
                        todayAccom?.name?.ifBlank { todayAccom.location } ?: "Accommodation"
                    } else {
                        dayActivities[index - 1].name
                    }

                    // Show drive segment
                    if (driveTo > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "%02d:%02d".format((cumulativeMinutes / 60) % 24, cumulativeMinutes % 60),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DraculaComment,
                                modifier = Modifier.width(48.dp),
                            )
                            Text(
                                text = "\uD83D\uDE97 Drive from $fromLabel (${formatMinutes(driveTo)})",
                                fontSize = 12.sp,
                                color = DraculaOrange,
                            )
                        }
                    }

                    cumulativeMinutes += driveTo
                    val arriveTime = cumulativeMinutes

                    // Activity card (clickable for editing)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                            .clickable { onEditActivity(activity.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DraculaCurrent),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // Time column
                            Column(
                                modifier = Modifier.width(52.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "%02d:%02d".format((arriveTime / 60) % 24, arriveTime % 60),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DraculaCyan,
                                )
                                Text(
                                    text = formatMinutes(activity.durationMinutes),
                                    fontSize = 11.sp,
                                    color = DraculaComment,
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Column(Modifier.weight(1f)) {
                                val catEmoji = try {
                                    com.example.traveltool.data.ActivityCategory.valueOf(activity.category).emoji
                                } catch (_: Exception) { "\uD83D\uDCCC" }

                                Text(
                                    text = "$catEmoji ${activity.name}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DraculaForeground,
                                )
                                Text(
                                    text = "\uD83D\uDCCD ${activity.location}",
                                    fontSize = 12.sp,
                                    color = DraculaComment,
                                )
                                if (activity.description.isNotBlank()) {
                                    Text(
                                        text = activity.description,
                                        fontSize = 11.sp,
                                        color = DraculaComment,
                                        maxLines = 2,
                                    )
                                }
                                if (activity.rating != null) {
                                    Text(
                                        text = "\u2B50 ${String.format("%.1f", activity.rating)}",
                                        fontSize = 12.sp,
                                        color = DraculaYellow,
                                    )
                                }
                                if (activity.eatingType.isNotBlank()) {
                                    Text(
                                        text = "\uD83C\uDF7D\uFE0F ${activity.eatingType}",
                                        fontSize = 12.sp,
                                        color = DraculaPurple,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { tripViewModel.deleteActivity(tripId, activity.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete activity",
                                    tint = DraculaRed,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    cumulativeMinutes += activity.durationMinutes
                }

                // Show return segment
                val returnToLabel = when {
                    isFinalDay -> trip.startingPoint.ifBlank { "Home" }
                    isMovingDay && nextDayAccom != null -> nextDayAccom.location.ifBlank { "Next accommodation" }
                    todayAccom != null -> todayAccom.name.ifBlank { todayAccom.location }
                    else -> "Accommodation"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "%02d:%02d".format((cumulativeMinutes / 60) % 24, cumulativeMinutes % 60),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DraculaComment,
                        modifier = Modifier.width(48.dp),
                    )
                    Text(
                        text = if (isFinalDay) "\uD83C\uDFE0 Head home to $returnToLabel"
                        else "\uD83C\uDFE8 Return to $returnToLabel",
                        fontSize = 12.sp,
                        color = DraculaPink,
                    )
                }

                // ── Day summary card ──
                Spacer(Modifier.height(8.dp))
                val totalDrivingMin = dayActivities.sumOf { it.drivingTimeToMinutes ?: 0 }
                val totalActivityMin = dayActivities.sumOf { it.durationMinutes }

                val endMinutes = cumulativeMinutes
                val finalH = (endMinutes / 60) % 24
                val finalM = endMinutes % 60

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DraculaPurple.copy(alpha = 0.12f)
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Day Summary", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DraculaPurple)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("\uD83D\uDE97 Total travel", fontSize = 12.sp, color = DraculaComment)
                                Text(formatMinutes(totalDrivingMin), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DraculaOrange)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("\u23F1 Total activity", fontSize = 12.sp, color = DraculaComment)
                                Text(formatMinutes(totalActivityMin), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DraculaGreen)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "Done at",
                                    fontSize = 12.sp, color = DraculaComment,
                                )
                                Text(
                                    "%02d:%02d".format(finalH, finalM),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DraculaPink,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Add Activity button ──
            Button(
                onClick = onAddActivity,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DraculaGreen,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Activity", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

/**
 * Find the accommodation whose date range covers the given day.
 */
private fun findAccommodationForDay(
    accommodations: List<Accommodation>,
    dayMillis: Long
): Accommodation? {
    val strict = accommodations.find { it.startMillis <= dayMillis && dayMillis < it.endMillis }
    if (strict != null) return strict
    return accommodations.find { it.startMillis <= dayMillis && dayMillis <= it.endMillis }
}

/** Format minutes into a human-readable string like "2h 30m" or "45m". */
private fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
