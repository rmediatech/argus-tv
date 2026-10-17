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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
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
 * One Job: Presents available TVs on the active Wi-Fi in an intuitive, consumer-grade card list.
 * Isolates other-location TVs quietly below the waterline with zero technical clutter.
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

    val activeDevices = discoveredDevices.filter { dev ->
        if (!activeNetworkId.isNullOrBlank() && dev.effectiveNetworkId == activeNetworkId) true
        else if (!activeSubnetPrefix.isNullOrBlank() && dev.effectiveSubnet == activeSubnetPrefix) true
        else activeNetworkId.isNullOrBlank() && activeSubnetPrefix.isNullOrBlank()
    }
    val otherDevices = discoveredDevices.filter { dev -> !activeDevices.any { it.ipAddress == dev.ipAddress } }
    val visibleActive = activeDevices.filter { !hiddenIps.contains(it.ipAddress) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select TV", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.textPrimary)
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
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isScanning) "Searching your Wi-Fi for TVs..." else "No TVs detected on this Wi-Fi yet.",
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
                            onSelect = { onSelectDevice(device); onDismiss() },
                            onDelete = { onDeleteDevice(device) }
                        )
                    }
                }

                if (otherDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(palette.surfaceElevated.copy(alpha = 0.6f))
                            .clickable { showOtherLocations = !showOtherLocations }.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TVs from other locations (${otherDevices.size})", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onClearOtherNetworks, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                Text("Clear", fontSize = 10.sp, color = Color(0xFFFF5252))
                            }
                            Icon(if (showOtherLocations) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (showOtherLocations) {
                        otherDevices.forEach { device ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).clickable { onSelectDevice(device); onDismiss() }) {
                                    Text(device.name, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                                    Text("${device.deviceType.displayName} • Saved Location", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, fontSize = 10.sp)
                                }
                                IconButton(onClick = { onDeleteDevice(device) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Rounded.Close, "Remove", tint = palette.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                                }
                            }
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
    onSelect: () -> Unit,
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
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Tv, contentDescription = null, tint = badgeColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = device.name,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = palette.textPrimary,
                    fontSize = 13.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isOnline) Color(0xFF00E676) else Color(0xFF9E9E9E)))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isOnline) "Online • ${device.deviceType.displayName}" else "Standby • ${device.deviceType.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOnline) palette.textPrimary.copy(alpha = 0.85f) else palette.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }

        if (isSelected) {
            Icon(Icons.Rounded.Check, contentDescription = "Active TV", tint = palette.primaryAccent, modifier = Modifier.size(18.dp))
        } else {
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = palette.textSecondary.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
            }
        }
    }
}
