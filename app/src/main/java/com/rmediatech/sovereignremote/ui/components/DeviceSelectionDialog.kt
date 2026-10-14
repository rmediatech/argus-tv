package com.rmediatech.sovereignremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette

/**
 * Sovereign Device Selection Dialog:
 * One Job: Presents discovered TVs on the active Wi-Fi, isolates saved TVs from other locations,
 * and enables clear/hide/delete actions with zero technical cognitive bloat.
 */
@Composable
fun DeviceSelectionDialog(
    discoveredDevices: List<RemoteDevice>,
    currentDevice: RemoteDevice?,
    hiddenIps: Set<String>,
    activeSubnetPrefix: String?,
    activeNetworkId: String? = null,
    isScanning: Boolean,
    onSelectDevice: (RemoteDevice) -> Unit,
    onToggleHideDevice: (String) -> Unit,
    onDeleteDevice: (RemoteDevice) -> Unit,
    onClearOtherNetworks: () -> Unit,
    onClearAllDevices: () -> Unit,
    onScanRequested: () -> Unit,
    onOpenManualIp: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalThemePalette.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Discovered Devices", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = palette.primaryAccent,
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        text = {
            Column {
                val activeDevices = discoveredDevices.filter {
                    if (!activeNetworkId.isNullOrBlank()) {
                        it.effectiveNetworkId == activeNetworkId
                    } else if (!activeSubnetPrefix.isNullOrBlank()) {
                        it.effectiveSubnet == activeSubnetPrefix
                    } else {
                        false
                    }
                }
                val otherDevices = discoveredDevices.filter {
                    if (!activeNetworkId.isNullOrBlank()) {
                        it.effectiveNetworkId != activeNetworkId
                    } else if (!activeSubnetPrefix.isNullOrBlank()) {
                        it.effectiveSubnet != activeSubnetPrefix
                    } else {
                        true
                    }
                }

                val visibleActive = activeDevices.filter { !hiddenIps.contains(it.ipAddress) }
                val hiddenDevices = discoveredDevices.filter { hiddenIps.contains(it.ipAddress) }
                var showHidden by remember { mutableStateOf(false) }
                var showOtherNetworks by remember { mutableStateOf(false) }

                // 1. ACTIVE WI-FI NETWORK SECTION
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DEVICES ON THIS WI-FI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryAccent
                    )
                    if (isScanning) {
                        Text("Scanning...", fontSize = 10.sp, color = palette.textSecondary)
                    }
                }

                if (visibleActive.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isScanning) "Searching active Wi-Fi for TVs..." else "No TVs found on this Wi-Fi yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        if (!isScanning) {
                            OutlinedButton(
                                onClick = onScanRequested,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Scan Wi-Fi", fontSize = 11.sp, color = palette.primaryAccent)
                            }
                        }
                    }
                } else {
                    visibleActive.forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (device.id == currentDevice?.id) palette.surfaceElevated else Color.Transparent)
                                .clickable {
                                    onSelectDevice(device)
                                    onDismiss()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val badgeColor = Color(device.deviceType.badgeColorHex)
                                Icon(Icons.Rounded.Tv, contentDescription = null, tint = badgeColor)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(device.name, fontWeight = FontWeight.SemiBold, color = palette.textPrimary, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(badgeColor.copy(alpha = 0.15f))
                                                .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = device.deviceType.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = badgeColor,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        "${device.ipAddress} • " + (if (device.isEthernet) "Ethernet" else "Wi-Fi") + (device.modelName?.let { " • $it" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onDeleteDevice(device) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove Device",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleHideDevice(device.ipAddress) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VisibilityOff,
                                        contentDescription = "Hide Device",
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. SAVED FROM OTHER LOCATIONS SECTION
                if (otherDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.surfaceElevated)
                            .clickable { showOtherNetworks = !showOtherNetworks }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saved Devices from Other Locations (${otherDevices.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = onClearOtherNetworks,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Clear", fontSize = 10.sp, color = Color(0xFFFF5252))
                            }
                            Icon(
                                imageVector = if (showOtherNetworks) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Toggle Other Networks",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (showOtherNetworks) {
                        otherDevices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onSelectDevice(device)
                                            onDismiss()
                                        }
                                ) {
                                    Text(device.name, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                                    Text(
                                        if (device.modelName != null) "${device.deviceType.displayName} • ${device.modelName}" else "${device.deviceType.displayName} • Saved Location",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteDevice(device) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove Device",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. HIDDEN DEVICES SECTION
                if (hiddenDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.surfaceElevated)
                            .clickable { showHidden = !showHidden }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hidden / Neighbor TVs (${hiddenDevices.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                        Icon(
                            imageVector = if (showHidden) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Toggle Hidden",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (showHidden) {
                        hiddenDevices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.name, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                                    Text(device.ipAddress, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onDeleteDevice(device) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Remove Device",
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    TextButton(onClick = { onToggleHideDevice(device.ipAddress) }) {
                                        Text("Unhide", fontSize = 11.sp, color = palette.primaryAccent)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = onScanRequested,
                    enabled = !isScanning
                ) {
                    Text("Scan Wi-Fi", color = palette.primaryAccent)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onOpenManualIp) {
                    Text("Add IP", color = palette.textPrimary)
                }
            }
        },
        dismissButton = {
            Row {
                if (discoveredDevices.isNotEmpty()) {
                    TextButton(onClick = {
                        onClearAllDevices()
                        onDismiss()
                    }) {
                        Text("Clear All", color = Color(0xFFFF5252))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("Close", color = palette.textSecondary)
                }
            }
        }
    )
}
