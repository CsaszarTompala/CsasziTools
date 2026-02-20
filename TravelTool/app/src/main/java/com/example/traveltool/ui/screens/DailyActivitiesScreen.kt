package com.example.traveltool.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.data.TripViewModel
import com.example.traveltool.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Standalone screen for daily activities.
 * Shows each trip day (including the final travel-home day) with
 * moving/staying status and accommodation info.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyActivitiesScreen(
    tripId: String,
    tripViewModel: TripViewModel,
    onDayClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val trip = tripViewModel.getTripById(tripId)

    if (trip == null) {
        onBack()
        return
    }

    val oneDayMs = 24 * 60 * 60 * 1000L

    // Generate list of days INCLUDING the final day (endMillis — traveling home)
    val tripDays = remember(trip.startMillis, trip.endMillis) {
        val days = mutableListOf<Long>()
        var d = trip.startMillis
        while (d <= trip.endMillis) {
            days.add(d)
            d += oneDayMs
        }
        days
    }

    val hasAnyAccommodation = trip.accommodations.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Activities") },
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
                    val dayDateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                    val dayLabel = dayDateFormat.format(Date(dayMillis))

                    val isFinalDay = dayMillis >= trip.endMillis

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
                        isFinalDay -> true  // final day — traveling home
                        coveringAccom == null || prevDayAccom == null -> false
                        coveringAccom.location != prevDayAccom.location -> true
                        else -> false
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 3.dp)
                            .then(
                                if (hasCoverage || isFinalDay) Modifier.clickable { onDayClick(dayMillis) }
                                else Modifier
                            ),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasCoverage || isFinalDay) DraculaCurrent else DraculaCurrent.copy(alpha = 0.4f)
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isFinalDay) "🏠" else if (isMoving) "🚗" else "🏨",
                                fontSize = 20.sp,
                                modifier = Modifier.width(32.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (isFinalDay) "Day $dayNumber — $dayLabel (Travel Home)"
                                           else "Day $dayNumber — $dayLabel",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (hasCoverage || isFinalDay) DraculaForeground else DraculaComment,
                                )
                                if (isFinalDay) {
                                    Text(
                                        text = "Traveling home from ${trip.location}",
                                        fontSize = 12.sp,
                                        color = DraculaOrange,
                                    )
                                } else if (coveringAccom != null && coveringAccom.location.isNotBlank()) {
                                    Text(
                                        text = coveringAccom.location,
                                        fontSize = 12.sp,
                                        color = DraculaComment,
                                    )
                                } else {
                                    Text(
                                        "No accommodation",
                                        fontSize = 12.sp,
                                        color = DraculaRed,
                                    )
                                }
                            }
                            if (hasCoverage || isFinalDay) {
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
}
