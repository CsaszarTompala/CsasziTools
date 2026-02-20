package com.example.traveltool.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.data.Accommodation
import com.example.traveltool.data.TripViewModel
import com.example.traveltool.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Displays all accommodations for a trip, ordered by date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccommodationListScreen(
    tripId: String,
    tripViewModel: TripViewModel,
    onAddAccommodation: (String) -> Unit,
    onEditAccommodation: (tripId: String, accomId: String) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val trip = tripViewModel.getTripById(tripId)

    if (trip == null) {
        onBack()
        return
    }

    val accommodations = trip.accommodations.sortedBy { it.startMillis }
    var accomToDelete by remember { mutableStateOf<Accommodation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accommodation") },
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
                        onClick = { onAddAccommodation(tripId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.green,
                            contentColor = MaterialTheme.colorScheme.background,
                        )
                    ) {
                        Text("+ Add Accommodation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Text(
                    text = "\"${trip.name}\" Accommodations",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }

            if (accommodations.isEmpty()) {
                item {
                    Text(
                        text = "No accommodations yet.",
                        fontSize = 14.sp,
                        color = colors.comment,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            items(accommodations, key = { it.id }) { accom ->
                AccommodationCard(
                    accom = accom,
                    onClick = { onEditAccommodation(tripId, accom.id) },
                    onDelete = { accomToDelete = accom }
                )
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    // --- Delete confirmation dialog ---
    accomToDelete?.let { accom ->
        AlertDialog(
            onDismissRequest = { accomToDelete = null },
            title = { Text("Delete Accommodation") },
            text = {
                Text(
                    "Are you sure you want to delete \"${accom.name.ifBlank { "(unnamed)" }}\"?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tripViewModel.deleteAccommodation(tripId, accom.id)
                        accomToDelete = null
                    }
                ) {
                    Text("Delete", color = colors.red)
                }
            },
            dismissButton = {
                TextButton(onClick = { accomToDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = colors.current,
            titleContentColor = colors.foreground,
            textContentColor = colors.foreground,
        )
    }
}

@Composable
private fun AccommodationCard(
    accom: Accommodation,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppColors.current
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val startDate = remember(accom.startMillis) {
        if (accom.startMillis > 0) dateFormat.format(Date(accom.startMillis)) else "—"
    }
    val endDate = remember(accom.endMillis) {
        if (accom.endMillis > 0) dateFormat.format(Date(accom.endMillis)) else "—"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.current,
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .padding(end = 32.dp),
            ) {
                Text(
                    text = accom.name.ifBlank { "(No name)" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.foreground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$startDate – $endDate",
                    fontSize = 14.sp,
                    color = colors.orange,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (accom.pricePerNight != null)
                        "💰 ${accom.pricePerNight} ${accom.priceCurrency}/night"
                    else
                        "💰 Price not set",
                    fontSize = 14.sp,
                    color = if (accom.pricePerNight != null) colors.green else colors.comment,
                )
                if (accom.location.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "📍 ${accom.location}",
                        fontSize = 13.sp,
                        color = colors.comment,
                    )
                }
            }

            // Warning + Delete icons
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (accom.hasWarning) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Incomplete accommodation",
                        tint = colors.yellow,
                        modifier = Modifier.padding(4.dp).size(20.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete accommodation",
                        tint = colors.red,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
