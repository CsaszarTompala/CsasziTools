package com.example.traveltool.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traveltool.ui.theme.*

/**
 * A compact currency selector dropdown, aligned to match OutlinedTextField height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPicker(
    selected: String,
    currencies: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Cur.", fontSize = 11.sp) },
            modifier = Modifier
                .menuAnchor()
                .width(100.dp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DraculaPurple,
                focusedLabelColor = DraculaPurple,
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            currencies.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    }
                )
            }
        }
    }
}
