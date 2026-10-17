package com.rmediatech.sovereignremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.protocols.VizioPairingChallenge
import com.rmediatech.sovereignremote.protocols.VizioSmartCastProtocol
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette
import kotlinx.coroutines.launch

/**
 * Sovereign Vizio PIN Pairing Dialog:
 * One Job: Prompts the user to enter the 4-digit numeric PIN displayed on their Vizio TV screen
 * to establish persistent, encrypted HTTPS communication without cloud dependencies.
 */
@Composable
fun VizioPinDialog(
    device: RemoteDevice,
    protocol: VizioSmartCastProtocol,
    challenge: VizioPairingChallenge,
    onDismiss: () -> Unit,
    onPaired: () -> Unit
) {
    val palette = LocalThemePalette.current
    val scope = rememberCoroutineScope()
    var pinText by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pair Vizio SmartCast",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "${device.name} • ${device.ipAddress}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primaryAccent,
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = palette.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.primaryAccent.copy(alpha = 0.1f))
                        .border(1.dp, palette.primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Look up at your TV screen. A 4-digit PIN is displayed. Enter it below to pair:",
                        fontSize = 12.sp,
                        color = palette.textPrimary
                    )
                }

                OutlinedTextField(
                    value = pinText,
                    onValueChange = { if (it.length <= 4) pinText = it.filter { char -> char.isDigit() } },
                    label = { Text("4-Digit PIN") },
                    leadingIcon = { Icon(Icons.Rounded.Pin, contentDescription = null, tint = palette.primaryAccent) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primaryAccent,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary
                    )
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = palette.textSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (pinText.length == 4) {
                            scope.launch {
                                isPairing = true
                                errorMessage = null
                                val result = protocol.submitPin(device.ipAddress, device.port, challenge, pinText)
                                isPairing = false
                                result.fold(
                                    onSuccess = {
                                        onPaired()
                                        onDismiss()
                                    },
                                    onFailure = { err ->
                                        errorMessage = err.message ?: "Pairing failed. Verify the PIN on your TV."
                                    }
                                )
                            }
                        }
                    },
                    enabled = pinText.length == 4 && !isPairing,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAccent)
                ) {
                    if (isPairing) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Pair", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}
