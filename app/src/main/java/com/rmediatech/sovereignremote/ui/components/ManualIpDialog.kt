package com.rmediatech.sovereignremote.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.sovereignremote.security.SovereignSanitizer
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette

/**
 * Sovereign Manual IP Dialog:
 * One Job: Allows manual TV IP input with real-time IPv4 format validation.
 */
@Composable
fun ManualIpDialog(
    onManualIpAdded: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalThemePalette.current
    var manualIpInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Text(
                text = "Manual TV IP Address",
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = manualIpInput,
                    onValueChange = {
                        manualIpInput = it.trim()
                        isError = manualIpInput.isNotBlank() && !SovereignSanitizer.isValidIpv4(manualIpInput)
                    },
                    label = { Text("e.g. 192.168.1.150") },
                    singleLine = true,
                    isError = isError,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        focusedBorderColor = palette.primaryAccent,
                        unfocusedBorderColor = palette.border,
                        errorBorderColor = Color(0xFFFF1744)
                    )
                )
                if (isError) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Must be a valid IPv4 address (e.g. 192.168.1.50)",
                        color = Color(0xFFFF1744),
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanIp = manualIpInput.trim()
                    if (SovereignSanitizer.isValidIpv4(cleanIp)) {
                        onManualIpAdded(cleanIp)
                        onDismiss()
                    } else {
                        isError = true
                    }
                },
                enabled = manualIpInput.isNotBlank() && !isError,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryAccent,
                    contentColor = palette.background
                )
            ) {
                Text("Connect", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.textSecondary)
            }
        }
    )
}
