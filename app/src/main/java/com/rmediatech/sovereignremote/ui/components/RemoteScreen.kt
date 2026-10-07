package com.rmediatech.sovereignremote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import com.rmediatech.sovereignremote.model.DeviceLivenessState
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.ui.theme.*

@Composable
fun RemoteScreen(
    currentDevice: RemoteDevice?,
    discoveredDevices: List<RemoteDevice>,
    hiddenIps: Set<String> = emptySet(),
    activeSubnetPrefix: String? = null,
    isScanning: Boolean,
    livenessState: DeviceLivenessState = DeviceLivenessState.DISCONNECTED,
    lastAckLatencyMs: Long? = null,
    isAckActive: Boolean = false,
    currentTheme: ThemePreset = ThemePreset.CYBER_TACTICAL,
    onSelectTheme: (ThemePreset) -> Unit = {},
    onScanRequested: () -> Unit,
    onSelectDevice: (RemoteDevice) -> Unit,
    onToggleHideDevice: (String) -> Unit = {},
    onDeleteDevice: (RemoteDevice) -> Unit = {},
    onClearOtherNetworks: () -> Unit = {},
    onClearAllDevices: () -> Unit = {},
    onManualIpAdded: (String) -> Unit,
    onSendKey: (RemoteKey) -> Unit
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showManualIpDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("") }
    val view = LocalView.current

    val infiniteTransition = rememberInfiniteTransition(label = "LivenessPulse")
    val wakingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wakingAlpha"
    )

    val palette = LocalThemePalette.current
    val icons = LocalThemeIcons.current
    val layout = LocalThemeLayout.current

    fun performHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    Scaffold(
        containerColor = palette.background
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ARGUS",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            ),
                            color = palette.primaryAccent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(palette.primaryAccent.copy(alpha = 0.15f))
                                .border(1.dp, palette.primaryAccent.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SOVEREIGN",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryAccent
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Theme Switcher Button
                        IconButton(
                            onClick = {
                                performHaptic()
                                showThemeDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = icons.themeSwitcher,
                                contentDescription = "Switch Theme",
                                tint = palette.primaryAccent
                            )
                        }

                        // Wi-Fi Refresh / Scan Button
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
                                    color = palette.primaryAccent,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Scan Wi-Fi",
                                    tint = palette.primaryAccent
                                )
                            }
                        }

                        // Unobtrusive About / Info Button
                        IconButton(
                            onClick = {
                                performHaptic()
                                showAboutDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "About Argus",
                                tint = palette.primaryAccent
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Target Device Card
                val cardBorderColor = if (isAckActive) {
                    if (lastAckLatencyMs == -1L) Color(0xFFFF1744) else palette.primaryAccent
                } else palette.border
                val cardBorderWidth = if (isAckActive) 1.5.dp else 1.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surface)
                        .border(cardBorderWidth, cardBorderColor, RoundedCornerShape(12.dp))
                        .clickable {
                            performHaptic()
                            showDeviceDialog = true
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dotColor = Color(livenessState.colorHex)
                        val dotAlpha = if (livenessState == DeviceLivenessState.WAKING) wakingAlpha else 1.0f

                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(dotColor.copy(alpha = dotAlpha))
                                .then(
                                    if (livenessState == DeviceLivenessState.ONLINE) {
                                        Modifier.border(2.dp, dotColor.copy(alpha = 0.35f), CircleShape)
                                    } else Modifier
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentDevice?.name ?: "No Device Selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.textPrimary,
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

                                    Spacer(modifier = Modifier.width(5.dp))
                                    val statusColor = Color(livenessState.colorHex)
                                    val badgeAlpha = if (livenessState == DeviceLivenessState.WAKING) wakingAlpha else 1.0f
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(statusColor.copy(alpha = if (livenessState == DeviceLivenessState.WAKING) badgeAlpha * 0.2f else 0.15f))
                                            .border(1.dp, statusColor.copy(alpha = if (livenessState == DeviceLivenessState.WAKING) badgeAlpha else 0.6f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = livenessState.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            val ackText = when {
                                lastAckLatencyMs != null && lastAckLatencyMs >= 0 -> " • ACK < ${lastAckLatencyMs.coerceAtLeast(1)}ms"
                                lastAckLatencyMs == -1L -> " • TIMEOUT"
                                else -> ""
                            }
                            Text(
                                text = currentDevice?.let { 
                                    "${it.ipAddress} • " + (if (it.isEthernet) "Ethernet" else "Wi-Fi") + (it.modelName?.let { m -> " ($m)" } ?: "") + ackText
                                } ?: (if (activeSubnetPrefix.isNullOrBlank()) "Tap to select or scan" else "Wi-Fi: $activeSubnetPrefix.* • Tap to scan"),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (lastAckLatencyMs == -1L) Color(0xFFFF1744) else palette.textSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Select",
                        tint = palette.textSecondary
                    )
                }
            }

            // --- TOP ACTION BUTTONS (POWER, BACK, HOME, OPTIONS) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (powerLabel, powerColor) = when (livenessState) {
                    DeviceLivenessState.STANDBY -> "WAKE" to Color(0xFFFFAB00)
                    DeviceLivenessState.WAKING -> "WAKING" to Color(0xFF00E5FF)
                    DeviceLivenessState.ONLINE -> "POWER" to palette.powerColor
                    DeviceLivenessState.DISCONNECTED -> "POWER" to palette.powerColor
                }
                TacticalRoundButton(
                    icon = icons.power,
                    label = powerLabel,
                    accentColor = powerColor,
                    onClick = {
                        performHaptic()
                        onSendKey(RemoteKey.POWER)
                    }
                )
                TacticalRoundButton(
                    icon = icons.back,
                    label = "BACK",
                    accentColor = palette.textPrimary,
                    onClick = {
                        performHaptic()
                        onSendKey(RemoteKey.BACK)
                    }
                )
                TacticalRoundButton(
                    icon = icons.home,
                    label = "HOME",
                    accentColor = palette.primaryAccent,
                    onClick = {
                        performHaptic()
                        onSendKey(RemoteKey.HOME)
                    }
                )
                TacticalRoundButton(
                    icon = icons.options,
                    label = "OPTIONS",
                    accentColor = palette.secondaryAccent,
                    onClick = {
                        performHaptic()
                        onSendKey(RemoteKey.INFO)
                    }
                )
            }

            // --- CENTRAL CONTROLLER (DYNAMIC THEME ARCHITECTURE) ---
            when (layout) {
                ThemeLayoutType.RADAR_CIRCULAR -> RadarCircularController(onSendKey, ::performHaptic)
                ThemeLayoutType.GEOMETRIC_PAD -> GeometricPadController(onSendKey, ::performHaptic)
                ThemeLayoutType.RETRO_CROSS -> RetroCrossController(onSendKey, ::performHaptic)
                ThemeLayoutType.STEALTH_COLUMNS -> StealthColumnsController(onSendKey, ::performHaptic)
            }

            // --- PLAYBACK CONTROLS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.REWIND) }) {
                    Icon(icons.rewind, contentDescription = "Rev", tint = palette.textSecondary, modifier = Modifier.size(28.dp))
                }
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceElevated)
                        .border(1.dp, palette.border, CircleShape)
                        .clickable { performHaptic(); onSendKey(RemoteKey.PLAY_PAUSE) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icons.playPause, contentDescription = "Play/Pause", tint = palette.primaryAccent, modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.FAST_FORWARD) }) {
                    Icon(icons.fastForward, contentDescription = "Fwd", tint = palette.textSecondary, modifier = Modifier.size(28.dp))
                }
            }

            // --- BOTTOM VOLUME & MUTE ROW ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.VOLUME_DOWN) }) {
                    Icon(icons.volDown, contentDescription = "Vol -", tint = palette.textPrimary)
                }
                IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.MUTE) }) {
                    Icon(icons.mute, contentDescription = "Mute", tint = palette.powerColor)
                }
                IconButton(onClick = { performHaptic(); onSendKey(RemoteKey.VOLUME_UP) }) {
                    Icon(icons.volUp, contentDescription = "Vol +", tint = palette.textPrimary)
                }
            }
        }

        // --- THEME SELECTION MODAL DIALOG ---
        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentTheme = currentTheme,
                onSelectTheme = onSelectTheme,
                onDismiss = { showThemeDialog = false }
            )
        }

        // --- DEVICE SELECTION MODAL DIALOG ---
        if (showDeviceDialog) {
            AlertDialog(
                onDismissRequest = { showDeviceDialog = false },
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
                            activeSubnetPrefix.isNullOrBlank() || it.effectiveSubnet == activeSubnetPrefix
                        }
                        val otherDevices = discoveredDevices.filter {
                            !activeSubnetPrefix.isNullOrBlank() && it.effectiveSubnet != activeSubnetPrefix
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
                                text = "CURRENT WI-FI" + (if (activeSubnetPrefix.isNullOrBlank()) "" else " ($activeSubnetPrefix.*)"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryAccent
                            )
                            if (isScanning) {
                                Text("Scanning...", fontSize = 10.sp, color = palette.textSecondary)
                            }
                        }

                        if (visibleActive.isEmpty()) {
                            Text(
                                text = if (isScanning) "Searching active Wi-Fi for TVs..." else "No TVs found on this Wi-Fi yet.\nTap 'Scan Wi-Fi' to discover devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            visibleActive.forEach { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (device.id == currentDevice?.id) palette.surfaceElevated else Color.Transparent)
                                        .clickable {
                                            onSelectDevice(device)
                                            showDeviceDialog = false
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

                        // 2. SAVED FROM OTHER NETWORKS SECTION
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
                                    text = "Saved from Other Networks (${otherDevices.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { onClearOtherNetworks() },
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
                                                    showDeviceDialog = false
                                                }
                                        ) {
                                            Text(device.name, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                                            Text("${device.ipAddress} (${device.effectiveSubnet}.*)", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, fontSize = 9.sp)
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
                            onClick = { onScanRequested() },
                            enabled = !isScanning
                        ) {
                            Text("Scan Wi-Fi", color = palette.primaryAccent)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = {
                            showDeviceDialog = false
                            showManualIpDialog = true
                        }) {
                            Text("Add IP", color = palette.textPrimary)
                        }
                    }
                },
                dismissButton = {
                    Row {
                        if (discoveredDevices.isNotEmpty()) {
                            TextButton(onClick = {
                                onClearAllDevices()
                                showDeviceDialog = false
                            }) {
                                Text("Clear All", color = Color(0xFFFF5252))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        TextButton(onClick = { showDeviceDialog = false }) {
                            Text("Close", color = palette.textSecondary)
                        }
                    }
                }
            )
        }

        // --- MANUAL IP INPUT DIALOG ---
        if (showManualIpDialog) {
            AlertDialog(
                onDismissRequest = { showManualIpDialog = false },
                containerColor = palette.surface,
                title = { Text("Manual TV IP Address", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary) },
                text = {
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = { manualIpInput = it },
                        label = { Text("e.g. 192.168.1.150") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.textPrimary,
                            unfocusedTextColor = palette.textPrimary,
                            focusedBorderColor = palette.primaryAccent,
                            unfocusedBorderColor = palette.border
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
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryAccent, contentColor = palette.background)
                    ) {
                        Text("Connect", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualIpDialog = false }) {
                        Text("Cancel", color = palette.textSecondary)
                    }
                }
            )
        }

        // --- ABOUT / INFO DIALOG ---
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                containerColor = palette.surface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = palette.primaryAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "About Argus",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            "Argus Sovereign Remote v1.0.0",
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryAccent,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "The all-seeing, zero-cloud universal device & smart TV controller. Communicates directly with Google TV, Android TV, Fire TV, Roku, Smart TVs, and Wake-on-LAN hosts over your local network.",
                            fontSize = 13.sp,
                            color = palette.textSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = palette.border)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "DEVELOPER & ARCHITECTURE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Crafted by RMediaTech",
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            "https://rmediatech.com",
                            color = palette.primaryAccent,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "100% Free • Zero Ads • Zero Cloud Relays • Pure Local Sovereign Engineering",
                            fontSize = 11.sp,
                            color = palette.textSecondary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text("Close", color = palette.primaryAccent, fontWeight = FontWeight.Bold)
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
    val palette = LocalThemePalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(palette.surface)
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
            color = palette.textSecondary
        )
    }
}
