package com.example.traveltool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.ui.theme.DraculaComment
import com.example.traveltool.ui.theme.DraculaCyan
import com.example.traveltool.ui.theme.DraculaGreen
import com.example.traveltool.ui.theme.DraculaPurple

/**
 * Welcome / home screen.
 *
 * Shows the CsasziTools + TravelTool branding and an "Add Trip" FAB.
 */
@Composable
fun HomeScreen(onAddTrip: () -> Unit) {
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTrip,
                containerColor = DraculaGreen,
                contentColor = MaterialTheme.colorScheme.background,
            ) {
                Text("+ Add Trip", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "CsasziTools",
                    fontSize = 16.sp,
                    color = DraculaComment,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "TravelTool",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = DraculaPurple,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "v0.0.1",
                    fontSize = 14.sp,
                    color = DraculaCyan,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
