package com.rmediatech.sovereignremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.net.DeviceLivenessTracker
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sovereign TV Setup Guide Dialog:
 * One Job: Displays an intuitive, consumer-grade 3-step setup guide for Fire TV and Google TV devices
 * when ADB Debugging (port 5555) is disabled or unconfigured on the TV.
 */
@Composable
fun TvSetupGuideDialog(
    device: RemoteDevice,
    onDismiss: () -> Unit,
    onDeviceReady: (RemoteDevice) -> Unit
) {
    val palette = LocalThemePalette.current
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Boolean?>(null) }

    val isFireTv = device.deviceType == DeviceType.FIRE_TV || device.name.contains("Fire", ignoreCase = true)
    val brandName = if (isFireTv) "Amazon Fire TV" else "Google / Android TV"

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
                        text = "$brandName Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "1-Time Setup • ${device.ipAddress}",
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
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFB300).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "To allow Argus to control ${device.name} over your local Wi-Fi, enable ADB control in your TV settings once:",
                        fontSize = 12.sp,
                        color = palette.textPrimary
                    )
                }

                if (isFireTv) {
                    SetupStepItem(
                        number = "1",
                        title = "Open Fire TV Settings",
                        description = "Using your TV remote, go to Settings ➔ My Fire TV ➔ About."
                    )
                    SetupStepItem(
                        number = "2",
                        title = "Unlock Developer Options",
                        description = "Click on your Fire TV's name 7 times rapidly until it says 'No need, you are already a developer'."
                    )
                    SetupStepItem(
                        number = "3",
                        title = "Turn On ADB Debugging",
                        description = "Go back one screen to Developer Options and toggle 'ADB Debugging' to ON."
                    )
                    SetupStepItem(
                        number = "4",
                        title = "Check TV Screen for Authorization",
                        description = "When prompted on your TV screen, check 'Always allow from this computer' and select OK."
                    )
                } else {
                    SetupStepItem(
                        number = "1",
                        title = "Open TV Settings",
                        description = "Go to Settings ➔ System ➔ About."
                    )
                    SetupStepItem(
                        number = "2",
                        title = "Unlock Developer Options",
                        description = "Scroll down to 'Android TV OS build' and click it 7 times."
                    )
                    SetupStepItem(
                        number = "3",
                        title = "Turn On USB Debugging",
                        description = "Go back to Developer Options and turn 'USB Debugging' to ON."
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (testResult != null) {
                    val success = testResult == true
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (success) Color(0xFF00E676).copy(alpha = 0.12f) else Color(0xFFFF5252).copy(alpha = 0.12f))
                            .border(1.dp, if (success) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFFFF5252).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = if (success) Color(0xFF00E676) else Color(0xFFFF5252),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (success) "Port 5555 is OPEN! TV is ready to control." else "Port 5555 is still closed. Ensure ADB Debugging is ON.",
                                fontSize = 11.sp,
                                color = if (success) Color(0xFF00E676) else Color(0xFFFF5252),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isChecking = true
                            val isOpen = withContext(Dispatchers.IO) {
                                DeviceLivenessTracker.isPortOpen(device.ipAddress, 5555, 600)
                            }
                            testResult = isOpen
                            isChecking = false
                            if (isOpen) {
                                onDeviceReady(device)
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isChecking,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = palette.primaryAccent)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Test Connection", fontSize = 11.sp, color = palette.primaryAccent)
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAccent),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text("Got It", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
private fun SetupStepItem(
    number: String,
    title: String,
    description: String
) {
    val palette = LocalThemePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(palette.primaryAccent.copy(alpha = 0.18f))
                .border(1.dp, palette.primaryAccent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.primaryAccent)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            Text(text = description, fontSize = 11.sp, color = palette.textSecondary, modifier = Modifier.padding(top = 1.dp))
        }
    }
}
