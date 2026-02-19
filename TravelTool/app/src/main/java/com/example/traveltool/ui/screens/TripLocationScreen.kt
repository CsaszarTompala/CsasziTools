package com.example.traveltool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Step 3 — choose trip location.
 *
 * For now this is a simple text input with a placeholder map area.
 * It will be upgraded to a full Google Maps integration later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripLocationScreen(
    tripName: String,
    startMillis: Long,
    endMillis: Long,
    onBack: () -> Unit
) {
    var location by remember { mutableStateOf("") }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val startDate  = remember(startMillis) { dateFormat.format(Date(startMillis)) }
    val endDate    = remember(endMillis)   { dateFormat.format(Date(endMillis)) }

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
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Button(
                        onClick = {
                            // TODO: save trip and navigate to trip summary
                        },
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

            // --- Trip summary chip ---
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

            // --- Location input ---
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location") },
                placeholder = { Text("e.g. Paris, France") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DraculaPurple,
                    focusedLabelColor = DraculaPurple,
                    cursorColor = DraculaPurple,
                )
            )

            Spacer(Modifier.height(24.dp))

            // --- Map placeholder ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DraculaCurrent),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗺️", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Map coming soon",
                        color = DraculaComment,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "Google Maps integration planned",
                        color = DraculaComment,
                        fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
