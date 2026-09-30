package com.rmediatech.sovereignremote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.ui.theme.*

@Composable
fun RemoteScreen(
    currentDevice: RemoteDevice?,
    discoveredDevices: List<RemoteDevice>,
    hiddenIps: Set<String> = emptySet(),
    isScanning: Boolean,
    onScanRequested: () -> Unit,
    onSelectDevice: (RemoteDevice) -> Unit,
    onToggleHideDevice: (String) -> Unit = {},
    onManualIpAdded: (String) -> Unit,
    onSendKey: (RemoteKey) -> Unit
) {
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showManualIpDialog by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("") }
    val view = LocalView.current

    fun performHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    Scaffold(
        containerColor = VoidBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- TOP HEADER & DEVICE BAR ---
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡ SOVEREIGN REMOTE",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeonCyan
                    )

                    IconButton(
                        onClick = {
                            performHaptic()
                            onScanRequested()
                        },
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = NeonCyan,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Scan Wi-Fi",
                                tint = NeonCyan
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Target Device Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                        .clickable {
                            performHaptic()
                            showDeviceDialog = true
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (currentDevice != null) EmeraldGreen else CrimsonRed)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentDevice?.name ?: "No Device Selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 14.sp
                                )
                                if (currentDevice != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val badgeColor = Color(currentDevice.deviceType.badgeColorHex)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(badgeColor.copy(alpha = 0.15f))
                                            .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = currentDevice.deviceType.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = badgeColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = currentDevice?.let { 
                                    "${it.ipAddress} • " + (if (it.isEthernet) "Ethernet" else "Wi-Fi") + (it.modelName?.let { m -> " ($m)" } ?: "") + (it.macAddress?.let { mac -> " • $mac" } ?: "")
                                } ?: "Tap to select or scan",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Select",
                        tint = TextSecondary
                    )
                }
            }

            // --- TOP ACTION BUTTONS (POWER, BACK, HOME, INFO) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TacticalRoundButton(
                    icon = Icons.Rounded.PowerSettingsNew,
                    label = "POWER",
                    accentColor = CrimsonRed,
                    onClick = {
                        performHaptic()
                        onSendKey(RemoteKey.POWER)
                    }
                )
                TacticalRoundButton(
                    icon = Icons.Rounded.ArrowBack,
                    label = "BACK",
                    accentColor = TextPrimary,
                    onClick = {
                        performHaptic()
                        onSendKey(RemoteKey.BACK)
                    }
                )
                TacticalRoundButton(
                    icon = Icons.Rounded.Home,
                    label = "HOME",
                    accentColor = NeonCyan,
                    onClick = {
                        performHaptic()
                        onSendKey(RemoteKey.HOME)
                    }
                )
                TacticalRoundButton(
                    icon = Icons.Rounded.Star,
                    label = "OPTIONS",
                    accentColor = TextSecondary,
                    onClick = {
                        performHaptic()
                        onSendKey(RemoteKey.INFO)
                    }
                )
            }

            // --- CENTRAL D-PAD CONTROLLER ---
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // UP
                IconButton(
                    onClick = { performHaptic(); onSendKey(RemoteKey.UP) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .size(56.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Up", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }

                // DOWN
                IconButton(
                    onClick = { performHaptic(); onSendKey(RemoteKey.DOWN) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .size(56.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Down", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }

                // LEFT
                IconButton(
                    onClick = { performHaptic(); onSendKey(RemoteKey.LEFT) },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp)
                        .size(56.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowLeft, contentDescription = "Left", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }

                // RIGHT
                IconButton(
                    onClick = { performHaptic(); onSendKey(RemoteKey.RIGHT) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .size(56.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Right", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }

                // CENTER OK / SELECT BUTTON
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.5.dp, NeonCyan, CircleShape)
                        .clickable {
                            performHaptic()
                            onSendKey(RemoteKey.SELECT)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "OK",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }
            }

            // --- PLAYBACK CONTROLS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.REWIND) }) {
                    Icon(Icons.Rounded.FastRewind, contentDescription = "Rev", tint = TextSecondary, modifier = Modifier.size(30.dp))
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, BorderSubtle, CircleShape)
                        .clickable { performHaptic(); onSendKey(RemoteKey.PLAY_PAUSE) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = NeonCyan, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.FAST_FORWARD) }) {
                    Icon(Icons.Rounded.FastForward, contentDescription = "Fwd", tint = TextSecondary, modifier = Modifier.size(30.dp))
                }
            }

            // --- BOTTOM VOLUME & MUTE ROW ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.VOLUME_DOWN) }) {
                    Icon(Icons.Rounded.VolumeDown, contentDescription = "Vol -", tint = TextPrimary)
                }
                IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.MUTE) }) {
                    Icon(Icons.Rounded.VolumeMute, contentDescription = "Mute", tint = CrimsonRed)
                }
                IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.VOLUME_UP) }) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = "Vol +", tint = TextPrimary)
                }
            }
        }

        // --- DEVICE SELECTION MODAL DIALOG ---
        if (showDeviceDialog) {
            AlertDialog(
                onDismissRequest = { showDeviceDialog = false },
                containerColor = SurfaceDark,
                title = {
                    Text("Discovered Devices", style = MaterialTheme.typography.titleMedium)
                },
                text = {
                    Column {
                        if (discoveredDevices.isEmpty()) {
                            Text(
                                "No devices discovered yet.\nTap 'Scan Wi-Fi' or enter IP manually.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        } else {
                            val visibleDevices = discoveredDevices.filter { !hiddenIps.contains(it.ipAddress) }
                            val hiddenDevices = discoveredDevices.filter { hiddenIps.contains(it.ipAddress) }
                            var showHidden by remember { mutableStateOf(false) }

                            if (visibleDevices.isEmpty()) {
                                Text(
                                    "All discovered devices are currently hidden.\nExpand hidden section below to unhide.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                visibleDevices.forEach { device ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (device.id == currentDevice?.id) SurfaceElevated else Color.Transparent)
                                            .clickable {
                                                onSelectDevice(device)
                                                showDeviceDialog = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val badgeColor = Color(device.deviceType.badgeColorHex)
                                            Icon(Icons.Rounded.Tv, contentDescription = null, tint = badgeColor)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(device.name, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(badgeColor.copy(alpha = 0.15f))
                                                            .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = device.deviceType.displayName,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = badgeColor,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                                Text(
                                                    "${device.ipAddress} • " + (if (device.isEthernet) "Ethernet" else "Wi-Fi") + (device.modelName?.let { " • $it" } ?: "") + (device.macAddress?.let { " • $it" } ?: ""),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { onToggleHideDevice(device.ipAddress) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.VisibilityOff,
                                                contentDescription = "Hide Device",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (hiddenDevices.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceDark)
                                        .clickable { showHidden = !showHidden }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Hidden / Neighbor TVs (${hiddenDevices.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    Icon(
                                        imageVector = if (showHidden) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Toggle Hidden",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                if (showHidden) {
                                    hiddenDevices.forEach { device ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(device.name, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                                Text(device.ipAddress, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                            }
                                            TextButton(onClick = { onToggleHideDevice(device.ipAddress) }) {
                                                Text("Unhide", fontSize = 11.sp, color = NeonCyan)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeviceDialog = false
                        showManualIpDialog = true
                    }) {
                        Text("Add IP Manually", color = NeonCyan)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeviceDialog = false }) {
                        Text("Close", color = TextSecondary)
                    }
                }
            )
        }

        // --- MANUAL IP INPUT DIALOG ---
        if (showManualIpDialog) {
            AlertDialog(
                onDismissRequest = { showManualIpDialog = false },
                containerColor = SurfaceDark,
                title = { Text("Manual TV IP Address", style = MaterialTheme.typography.titleMedium) },
                text = {
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = { manualIpInput = it },
                        label = { Text("e.g. 192.168.1.150") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderSubtle
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (manualIpInput.isNotBlank()) {
                                onManualIpAdded(manualIpInput.trim())
                                showManualIpDialog = false
                                manualIpInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = VoidBlack)
                    ) {
                        Text("Connect", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualIpDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
fun TacticalRoundButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(SurfaceDark)
                .border(1.dp, accentColor.copy(alpha = 0.6f), CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = TextSecondary
        )
    }
}
