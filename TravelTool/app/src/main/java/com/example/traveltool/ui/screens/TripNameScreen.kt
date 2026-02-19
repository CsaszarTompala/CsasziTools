package com.example.traveltool.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.ui.theme.DraculaGreen
import com.example.traveltool.ui.theme.DraculaPurple

/**
 * Step 1 — ask the user for a trip name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripNameScreen(
    onNext: (String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Trip") },
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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "What will this trip be called?",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = DraculaPurple,
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Trip name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DraculaPurple,
                    cursorColor = DraculaPurple,
                    focusedLabelColor = DraculaPurple,
                )
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { onNext(name.trim()) },
                enabled = name.isNotBlank(),
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
