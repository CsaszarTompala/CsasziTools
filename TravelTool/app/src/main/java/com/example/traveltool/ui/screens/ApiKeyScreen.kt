package com.example.traveltool.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.traveltool.data.ApiKeyStore
import com.example.traveltool.ui.theme.*

/**
 * General settings screen for AI configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf(ApiKeyStore.getOpenAiKey(context)) }
    var model by remember { mutableStateOf(ApiKeyStore.getOpenAiModel(context)) }

    val availableModels = listOf(
        "gpt-4o-mini",
        "gpt-4o",
        "gpt-4.1-mini"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "AI Settings",
                style = MaterialTheme.typography.titleMedium,
                color = DraculaPurple,
            )

            // API Key
            Text(
                text = "This key is used for AI-powered features like toll finding. It is stored encrypted on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = DraculaComment,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("OpenAI API Key") },
                placeholder = { Text("sk-...") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DraculaPurple,
                    focusedLabelColor = DraculaPurple,
                    cursorColor = DraculaPurple,
                )
            )

            // Model selection
            Text(
                text = "Model",
                style = MaterialTheme.typography.bodyMedium,
                color = DraculaForeground,
            )
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = model,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DraculaPurple,
                        focusedLabelColor = DraculaPurple,
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableModels.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m) },
                            onClick = {
                                model = m
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    ApiKeyStore.resetOpenAiModel(context)
                    model = ApiKeyStore.getOpenAiModel(context)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reset model to default")
            }

            Button(
                onClick = {
                    ApiKeyStore.setOpenAiKey(context, apiKey.trim())
                    ApiKeyStore.setOpenAiModel(context, model)
                    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DraculaGreen, contentColor = DraculaBackground),
            ) {
                Text("Save")
            }
        }
    }
}
