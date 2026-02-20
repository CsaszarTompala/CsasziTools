package com.example.traveltool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.ui.theme.DraculaGreen
import com.example.traveltool.ui.theme.DraculaPurple

/**
 * Step 2 — pick start and end dates with the Material 3 DateRangePicker.
 *
 * The user taps once to set the start date, then again for the end date.
 * Tapping a third time resets the selection (built-in behaviour).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDatesScreen(
    tripName: String,
    onNext: (startMillis: Long, endMillis: Long) -> Unit,
    onBack: () -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState(
        initialDisplayMode = DisplayMode.Picker
    )

    val startMillis = dateRangePickerState.selectedStartDateMillis
    val endMillis   = dateRangePickerState.selectedEndDateMillis
    val rangeReady  = startMillis != null && endMillis != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Dates") },
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
                        onClick = { onNext(startMillis!!, endMillis!!) },
                        enabled = rangeReady,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DraculaGreen,
                            contentColor = MaterialTheme.colorScheme.background,
                        )
                    ) {
                        Text("Next", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = "When is \"$tripName\"?",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = DraculaPurple,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                showModeToggle = false,
                title = null,
                headline = {
                    Text(
                        text = if (rangeReady) "Dates selected ✓"
                               else if (startMillis != null) "Now pick the end date"
                               else "Tap the start date",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background,
                    selectedDayContainerColor = DraculaPurple,
                    todayDateBorderColor = DraculaPurple,
                    dayInSelectionRangeContainerColor = DraculaPurple.copy(alpha = .25f),
                    todayContentColor = DraculaPurple,
                ),
            )
        }
    }
}
