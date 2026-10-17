package com.rmediatech.sovereignremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.ui.theme.LocalThemePalette

/**
 * Sovereign Device Selection Dialog:
 * One Job: Presents available TVs scoped by network, with individual Hide & Trashcan controls.
 * Clear distinction between active Wi-Fi, hidden devices, and other network locations.
 */
@Composable
fun DeviceSelectionDialog(
    discoveredDevices: List<RemoteDevice>,
    currentDevice: RemoteDevice?,
    hiddenIps: Set<String>,
    activeSubnetPrefix: String?,
    activeNetworkId: String? = null,
    activeSessionIps: Set<String> = emptySet(),
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
    var showOtherLocations by remember { mutableStateOf(false) }
    var showHiddenDevices by remember { mutableStateOf(false) }

    val activeDevices = discoveredDevices.filter { dev ->
        if (!activeNetworkId.isNullOrBlank() && dev.effectiveNetworkId == activeNetworkId) true
        else if (!activeSubnetPrefix.isNullOrBlank() && dev.effectiveSubnet == activeSubnetPrefix) true
        else activeNetworkId.isNullOrBlank() && activeSubnetPrefix.isNullOrBlank()
    }
    val otherDevices = discoveredDevices.filter { dev -> !activeDevices.any { it.ipAddress == dev.ipAddress } }
    val visibleActive = activeDevices.filter { !hiddenIps.contains(it.ipAddress) }
    val hiddenActive = activeDevices.filter { hiddenIps.contains(it.ipAddress) }

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
                    Text("Select TV", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                    val netLabel = if (!activeSubnetPrefix.isNullOrBlank()) "Wi-Fi: ${activeSubnetPrefix}.x" else "Local Network"
                    Text(netLabel, style = MaterialTheme.typography.labelSmall, color = palette.primaryAccent, fontSize = 10.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = palette.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TVs ON THIS WI-FI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.primaryAccent)
                    if (isScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Searching...", fontSize = 10.sp, color = palette.textSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = palette.primaryAccent, strokeWidth = 1.5.dp)
                        }
                    }
                }

                if (visibleActive.isEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isScanning) "Searching your Wi-Fi for TVs..." else "No visible TVs detected on this Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (!isScanning) {
                            OutlinedButton(onClick = onScanRequested, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Scan Again", fontSize = 11.sp, color = palette.primaryAccent)
                            }
                        }
                    }
                } else {
                    visibleActive.forEach { device ->
                        DeviceCard(
                            device = device,
                            isSelected = device.id == currentDevice?.id,
                            isOnline = activeSessionIps.contains(device.ipAddress),
                            isHidden = false,
                            onSelect = { onSelectDevice(device); onDismiss() },
                            onToggleHide = { onToggleHideDevice(device.ipAddress) },
                            onDelete = { onDeleteDevice(device) }
                        )
                    }
                }

                // Hidden devices on active Wi-Fi section
                if (hiddenActive.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(palette.surfaceElevated.copy(alpha = 0.6f))
                            .clickable { showHiddenDevices = !showHiddenDevices }.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hidden TVs on this Wi-Fi (${hiddenActive.size})", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                        Icon(if (showHiddenDevices) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                    }

                    if (showHiddenDevices) {
                        hiddenActive.forEach { device ->
                            DeviceCard(
                                device = device,
                                isSelected = false,
                                isOnline = activeSessionIps.contains(device.ipAddress),
                                isHidden = true,
                                onSelect = { onSelectDevice(device); onDismiss() },
                                onToggleHide = { onToggleHideDevice(device.ipAddress) },
                                onDelete = { onDeleteDevice(device) }
                            )
                        }
                    }
                }

                // Devices saved from other networks section
                if (otherDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(palette.surfaceElevated.copy(alpha = 0.6f))
                            .clickable { showOtherLocations = !showOtherLocations }.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("TVs from other networks (${otherDevices.size})", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onClearOtherNetworks, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                Text("Clear", fontSize = 10.sp, color = Color(0xFFFF5252))
                            }
                            Icon(if (showOtherLocations) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (showOtherLocations) {
                        otherDevices.forEach { device ->
                            DeviceCard(
                                device = device,
                                isSelected = false,
                                isOnline = false,
                                isHidden = hiddenIps.contains(device.ipAddress),
                                networkSubtitle = "${device.deviceType.displayName} • Network ${device.effectiveSubnet}.x (${device.ipAddress})",
                                onSelect = { onSelectDevice(device); onDismiss() },
                                onToggleHide = { onToggleHideDevice(device.ipAddress) },
                                onDelete = { onDeleteDevice(device) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpenManualIp, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("+ Connect by IP", fontSize = 11.sp, color = palette.textSecondary)
                }
                Row {
                    if (!isScanning) {
                        TextButton(onClick = onScanRequested, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("Rescan", fontSize = 12.sp, color = palette.primaryAccent)
                        }
                    }
                    TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("Done", fontSize = 12.sp, color = palette.textPrimary)
                    }
                }
            }
        }
    )
}

@Composable
private fun DeviceCard(
    device: RemoteDevice,
    isSelected: Boolean,
    isOnline: Boolean,
    isHidden: Boolean = false,
    networkSubtitle: String? = null,
    onSelect: () -> Unit,
    onToggleHide: (() -> Unit)? = null,
    onDelete: () -> Unit
) {
    val palette = LocalThemePalette.current
    val badgeColor = Color(device.deviceType.badgeColorHex)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) palette.surfaceElevated else Color.Transparent)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) palette.primaryAccent.copy(alpha = 0.6f) else palette.border.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onSelect() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = if (isHidden) 0.08f else 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Tv,
                    contentDescription = null,
                    tint = if (isHidden) badgeColor.copy(alpha = 0.45f) else badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isHidden) palette.textSecondary else palette.textPrimary,
                        fontSize = 13.sp
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Active TV",
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color(0xFF00E676) else Color(0xFF9E9E9E))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    val sub = networkSubtitle ?: "${if (isOnline) "Online" else "Standby"} • ${device.deviceType.displayName} • ${device.ipAddress}"
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOnline) palette.textPrimary.copy(alpha = 0.85f) else palette.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onToggleHide != null) {
                IconButton(onClick = onToggleHide, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = if (isHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = if (isHidden) "Unhide TV" else "Hide TV",
                        tint = if (isHidden) palette.primaryAccent else palette.textSecondary.copy(alpha = 0.65f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Forget TV",
                    tint = Color(0xFFFF5252).copy(alpha = 0.85f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
