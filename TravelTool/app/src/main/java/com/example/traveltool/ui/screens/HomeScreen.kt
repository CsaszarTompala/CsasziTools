package com.example.traveltool.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.R
import com.example.traveltool.data.Trip
import com.example.traveltool.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Welcome / home screen.
 *
 * Shows the CsasziTools + TravelTool branding, saved trips, and an "Add Trip" FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    trips: List<Trip>,
    onAddTrip: () -> Unit,
    onTripClick: (String) -> Unit,
    onDeleteTrip: (String) -> Unit,
    onSettings: () -> Unit
) {
    var tripToDelete by remember { mutableStateOf<Trip?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTrip,
                containerColor = DraculaGreen,
                contentColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.navigationBarsPadding(),
            ) {
                Text("+ Add Trip", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // --- Branding header ---
            item {
                Spacer(Modifier.height(32.dp))
                Image(
                    painter = painterResource(id = R.drawable.logo_main),
                    contentDescription = "TravelTool Logo",
                    modifier = Modifier
                        .padding(horizontal = 48.dp)
                        .fillMaxWidth(),
                )
                Spacer(Modifier.height(32.dp))
            }

            // --- Saved trips ---
            if (trips.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Trips",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DraculaForeground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }

                items(trips, key = { it.id }) { trip ->
                    TripCard(
                        trip = trip,
                        onClick = { onTripClick(trip.id) },
                        onDelete = { tripToDelete = trip }
                    )
                }

                item {
                    Spacer(Modifier.height(80.dp)) // room for FAB
                }
            }
        }
    }

    // --- Delete confirmation dialog ---
    tripToDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            title = { Text("Delete Trip") },
            text = { Text("Are you sure you want to delete \"${trip.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTrip(trip.id)
                        tripToDelete = null
                    }
                ) {
                    Text("Delete", color = DraculaRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { tripToDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = DraculaCurrent,
            titleContentColor = DraculaForeground,
            textContentColor = DraculaForeground,
        )
    }
}

@Composable
private fun TripCard(
    trip: Trip,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val startDate  = remember(trip.startMillis) { dateFormat.format(Date(trip.startMillis)) }
    val endDate    = remember(trip.endMillis)   { dateFormat.format(Date(trip.endMillis)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = DraculaCurrent,
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .padding(end = 32.dp),
            ) {
                Text(
                    text = trip.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DraculaPurple,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$startDate – $endDate",
                    fontSize = 14.sp,
                    color = DraculaOrange,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "📍 ${trip.location}",
                    fontSize = 14.sp,
                    color = DraculaForeground,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete trip",
                    tint = DraculaRed,
                )
            }
            if (trip.hasWarning) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Trip has warnings",
                    tint = DraculaYellow,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(20.dp),
                )
            }
        }
    }
}
