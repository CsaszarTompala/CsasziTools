package com.example.traveltool.ui.screens

import android.location.Geocoder
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Step 3 — choose trip location with Google Maps and geocoding search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripLocationScreen(
    tripName: String,
    startMillis: Long,
    endMillis: Long,
    onSave: (location: String) -> Unit,
    onBack: () -> Unit
) {
    var location by remember { mutableStateOf("") }
    var markerPosition by remember { mutableStateOf<LatLng?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val startDate  = remember(startMillis) { dateFormat.format(Date(startMillis)) }
    val endDate    = remember(endMillis)   { dateFormat.format(Date(endMillis)) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(48.8566, 2.3522), 5f)
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
                } catch (_: Exception) {
                    null
                }
            }
            if (result != null) {
                markerPosition = result
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(result, 12f)
                )
            } else {
                Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Location") },
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
                        onClick = { onSave(location.trim()) },
                        enabled = location.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DraculaGreen,
                            contentColor = MaterialTheme.colorScheme.background,
                        )
                    ) {
                        Text("Save Trip", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            Text(
                text = "\"$tripName\"  •  $startDate – $endDate",
                fontSize = 14.sp,
                color = DraculaComment,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Where will this trip be?",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = DraculaPurple,
            )

            Spacer(Modifier.height(16.dp))

            // --- Location input with search button ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
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
                    enabled = location.isNotBlank(),
                    modifier = Modifier
                        .height(56.dp)
                        .width(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = DraculaPurple,
                        contentColor = DraculaForeground,
                    )
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search location")
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- Google Map ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                ) {
                    markerPosition?.let { pos ->
                        Marker(
                            state = MarkerState(position = pos),
                            title = location.ifBlank { "Trip location" },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
