package com.example.moneysplitter.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.moneysplitter.data.ApiKeyStore
import com.example.moneysplitter.data.Expense
import com.example.moneysplitter.logic.ReceiptScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScanReceiptSheet(
    people: List<String>,
    currencies: List<String>,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onItemsAdded: (List<Expense>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isoFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    var isScanning by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasApiKey by remember { mutableStateOf(ApiKeyStore.hasApiKey(context)) }
    var apiKeyInput by remember { mutableStateOf("") }
    val scannedItems = remember { mutableStateListOf<ReceiptScanner.ReceiptItem>() }
    var selectedPayer by remember { mutableStateOf(people.firstOrNull() ?: "") }

    // Per-item split: index -> set of selected people. Empty/missing = everyone.
    val itemSplits = remember { mutableStateMapOf<Int, Set<String>>() }

    var photoUri by remember { mutableStateOf<Uri?>(null) }

    fun processImage(imageBytes: ByteArray) {
        val apiKey = ApiKeyStore.getApiKey(context) ?: return
        isScanning = true
        errorMessage = null
        scannedItems.clear()
        itemSplits.clear()

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ReceiptScanner.scan(imageBytes, apiKey, defaultCurrency)
            }
            isScanning = false
            if (result.error != null) {
                errorMessage = result.error
            }
            if (result.items.isNotEmpty()) {
                scannedItems.addAll(result.items)
            }
        }
    }

    fun compressImage(bytes: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        // Send full resolution — OpenAI's "high" detail mode handles tiling
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(photoUri!!)?.readBytes() }
                if (bytes != null) processImage(compressImage(bytes))
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.readBytes() }
                if (bytes != null) processImage(compressImage(bytes))
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val photoFile = File(context.cacheDir, "receipt_photos").apply { mkdirs() }
                .let { File(it, "receipt_${System.currentTimeMillis()}.jpg") }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            photoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val photoFile = File(context.cacheDir, "receipt_photos").apply { mkdirs() }
                .let { File(it, "receipt_${System.currentTimeMillis()}.jpg") }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            photoUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Scan Receipt", style = MaterialTheme.typography.headlineSmall) }

            // API key setup
            if (!hasApiKey) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("OpenAI API Key Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Enter your OpenAI API key to enable receipt scanning.", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = apiKeyInput, onValueChange = { apiKeyInput = it },
                                label = { Text("API Key") }, placeholder = { Text("sk-...") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) }
                            )
                            Button(
                                onClick = { ApiKeyStore.setApiKey(context, apiKeyInput); hasApiKey = true; apiKeyInput = "" },
                                enabled = apiKeyInput.startsWith("sk-") && apiKeyInput.length > 10,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save Key") }
                        }
                    }
                }
            }

            // Photo buttons + payer (before scan)
            if (hasApiKey && scannedItems.isEmpty() && !isScanning) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { launchCamera() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Camera")
                        }
                        OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Gallery")
                        }
                    }
                }
                item {
                    Column {
                        Text("Who paid?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            people.forEach { person ->
                                FilterChip(
                                    selected = selectedPayer == person,
                                    onClick = { selectedPayer = person },
                                    label = { Text(person) },
                                    leadingIcon = if (selectedPayer == person) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }

            // Scanning
            if (isScanning) {
                item {
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("Analyzing receipt...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Error
            if (errorMessage != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(errorMessage!!, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { errorMessage = null; launchCamera() }, modifier = Modifier.weight(1f)) { Text("Retry Camera") }
                        OutlinedButton(onClick = { errorMessage = null; galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("Retry Gallery") }
                    }
                }
            }

            // Results: per-item cards with people toggle buttons
            if (scannedItems.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${scannedItems.size} items found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Tap names to assign. No selection = split among everyone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Payer selector (also visible in results)
                item {
                    Column {
                        Text("Paid by:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            people.forEach { person ->
                                FilterChip(
                                    selected = selectedPayer == person,
                                    onClick = { selectedPayer = person },
                                    label = { Text(person) },
                                    leadingIcon = if (selectedPayer == person) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                itemsIndexed(scannedItems) { index, item ->
                    val currentSplit = itemSplits[index] ?: emptySet()

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Name + amount
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(
                                    formatReceiptAmount(item.amount, item.currency),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            // People toggle buttons
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                people.forEach { person ->
                                    val isSelected = person in currentSplit
                                    Surface(
                                        onClick = {
                                            val updated = if (isSelected) currentSplit - person else currentSplit + person
                                            if (updated.isEmpty()) itemSplits.remove(index) else itemSplits[index] = updated
                                        },
                                        shape = RoundedCornerShape(50),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                            }
                                            Text(
                                                person,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Action buttons
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { scannedItems.clear(); itemSplits.clear(); errorMessage = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Rescan")
                        }
                        Button(
                            onClick = {
                                val today = isoFormat.format(Date())
                                val expenses = scannedItems.mapIndexed { idx, item ->
                                    val split = itemSplits[idx]
                                    val splitList = if (split.isNullOrEmpty()) people else split.toList()
                                    Expense(
                                        id = UUID.randomUUID().toString(),
                                        amount = item.amount,
                                        currency = item.currency,
                                        paidBy = selectedPayer,
                                        splitAmong = splitList,
                                        description = item.name,
                                        name = item.name,
                                        date = today
                                    )
                                }
                                onItemsAdded(expenses)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = selectedPayer.isNotBlank()
                        ) { Text("Add ${scannedItems.size} items") }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

private fun formatReceiptAmount(amount: Double, currency: String): String {
    return if (amount == amount.toLong().toDouble()) "%,.0f %s".format(amount, currency)
    else "%,.2f %s".format(amount, currency)
}
