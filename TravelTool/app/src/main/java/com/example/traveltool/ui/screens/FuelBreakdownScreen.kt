package com.example.traveltool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.data.buildTripDriveSegments
import com.example.traveltool.data.CurrencyManager
import com.example.traveltool.data.TripViewModel
import com.example.traveltool.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class representing a single drive segment for the fuel breakdown.
 */
private data class DriveSegment(
    val dayMillis: Long,
    val dayNumber: Int,
    val fromLabel: String,
    val toLabel: String,
    val distanceKm: Double,
    val fuelLitres: Double,
    val fuelCost: Double,
)

/**
 * Screen that lists every drive segment chronologically with distances,
 * fuel consumption, and cost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelBreakdownScreen(
    tripId: String,
    tripViewModel: TripViewModel,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val trip = tripViewModel.getTripById(tripId)
    if (trip == null) { onBack(); return }

    val context = LocalContext.current
    val eurRates = remember(trip) { CurrencyManager.loadCachedRates(context) }

    val consumption = trip.fuelConsumption ?: 0.0  // L/100km
    val pricePerLiter = trip.fuelPricePerLiter ?: 0.0
    val currency = trip.fuelPriceCurrency

    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

    // Build chronological list of all drive segments (shared with toll finder logic)
    val segments = remember(trip, consumption, pricePerLiter) {
        buildTripDriveSegments(trip).map { segment ->
            val litres = (segment.distanceKm / 100.0) * consumption
            val cost = litres * pricePerLiter
            DriveSegment(
                dayMillis = segment.dayMillis,
                dayNumber = segment.dayNumber,
                fromLabel = segment.fromLabel,
                toLabel = segment.toLabel,
                distanceKm = segment.distanceKm,
                fuelLitres = litres,
                fuelCost = cost,
            )
        }
    }

    val totalKm = segments.sumOf { it.distanceKm }
    val totalLitres = segments.sumOf { it.fuelLitres }
    val totalCost = segments.sumOf { it.fuelCost }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fuel Breakdown") },
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
            // ── Totals summary card ──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.green.copy(alpha = 0.12f)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Total Fuel Summary",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.green,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text("\uD83D\uDEE3\uFE0F Distance", fontSize = 12.sp, color = colors.comment)
                                Text("${totalKm.toInt()} km", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("\u26FD Fuel", fontSize = 12.sp, color = colors.comment)
                                Text("%.1f L".format(totalLitres), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.orange)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("\uD83D\uDCB0 Cost", fontSize = 12.sp, color = colors.comment)
                                Text(
                                    "${CurrencyManager.formatAmount(totalCost, currency, eurRates)} $currency",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.green,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "%.1f L/100km  \u2022  ${CurrencyManager.formatAmount(pricePerLiter, currency, eurRates)}/$currency per L".format(consumption),
                            fontSize = 11.sp,
                            color = colors.comment,
                        )
                    }
                }

                if (segments.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No drive segments with distances found.\nOpen each day's detail to calculate driving distances.",
                        fontSize = 13.sp,
                        color = colors.comment,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            // ── Drive segment cards grouped by day ──
            val groupedByDay = segments.groupBy { it.dayMillis }
            groupedByDay.forEach { (dayMillis, daySegments) ->
                val dayNumber = daySegments.first().dayNumber
                val dayLabel = dateFormat.format(Date(dayMillis))
                val dayTotalKm = daySegments.sumOf { it.distanceKm }
                val dayTotalLitres = daySegments.sumOf { it.fuelLitres }
                val dayTotalCost = daySegments.sumOf { it.fuelCost }

                // Day header
                item {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "\uD83D\uDE97 Day $dayNumber \u2014 $dayLabel",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.foreground,
                        )
                        Text(
                            "${dayTotalKm.toInt()} km  \u2022  %.1f L  \u2022  ${CurrencyManager.formatAmount(dayTotalCost, currency, eurRates)} $currency".format(dayTotalLitres),
                            fontSize = 11.sp,
                            color = colors.comment,
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        color = colors.comment.copy(alpha = 0.3f),
                    )
                }

                // Individual segment cards
                itemsIndexed(daySegments) { _, segment ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 3.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.current.copy(alpha = 0.8f)
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Distance badge
                            Column(
                                modifier = Modifier.width(60.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "${segment.distanceKm.toInt()} km",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.orange,
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            // From → To and fuel details
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "\uD83D\uDCCD ${segment.fromLabel}  \u2192  ${segment.toLabel}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.foreground,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "\u26FD %.1f L  \u2022  ${CurrencyManager.formatAmount(segment.fuelCost, currency, eurRates)} $currency".format(segment.fuelLitres),
                                    fontSize = 12.sp,
                                    color = colors.comment,
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
