package com.rmediatech.sovereignremote.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.rmediatech.sovereignremote.model.DeviceLivenessState
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.ui.components.controllers.*
import com.rmediatech.sovereignremote.ui.theme.*

/**
 * Sovereign Argus Remote Screen:
 * Orchestrates the modular top header, tactile action bar, theme D-pad controller,
 * media/volume cluster, and system dialogs with zero monolithic bloat.
 */
@Composable
fun RemoteScreen(
    currentDevice: RemoteDevice?,
    discoveredDevices: List<RemoteDevice>,
    hiddenIps: Set<String> = emptySet(),
    activeSubnetPrefix: String? = null,
    activeNetworkId: String? = null,
    activeSessionIps: Set<String> = emptySet(),
    unconfiguredIps: Set<String> = emptySet(),
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
    var setupGuideDevice by remember { mutableStateOf<RemoteDevice?>(null) }
    val view = LocalView.current

    val palette = LocalThemePalette.current
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
            // 1. TOP HEADER & ACTIVE TARGET DEVICE BAR
            RemoteHeaderBar(
                currentDevice = currentDevice,
                isScanning = isScanning,
                livenessState = livenessState,
                lastAckLatencyMs = lastAckLatencyMs,
                isUnconfigured = currentDevice != null && unconfiguredIps.contains(currentDevice.ipAddress),
                onOpenThemeDialog = { showThemeDialog = true },
                onScanRequested = onScanRequested,
                onOpenAboutDialog = { showAboutDialog = true },
                onOpenDeviceDialog = { showDeviceDialog = true },
                onOpenSetupGuide = {
                    currentDevice?.let { setupGuideDevice = it }
                },
                performHaptic = ::performHaptic
            )

            // 2. TOP ACTION BUTTONS (POWER, BACK, HOME, OPTIONS)
            TacticalActionBar(
                livenessState = livenessState,
                onSendKey = onSendKey,
                performHaptic = ::performHaptic
            )

            // 3. CENTRAL THEME CONTROLLER
            when (layout) {
                ThemeLayoutType.RADAR_CIRCULAR -> RadarCircularController(onSendKey, ::performHaptic)
                ThemeLayoutType.GEOMETRIC_PAD -> GeometricPadController(onSendKey, ::performHaptic)
                ThemeLayoutType.RETRO_CROSS -> RetroCrossController(onSendKey, ::performHaptic)
                ThemeLayoutType.STEALTH_COLUMNS -> StealthColumnsController(onSendKey, ::performHaptic)
            }

            // 4. PLAYBACK CONTROLS & VOLUME/MUTE BAR
            PlaybackVolumeBar(
                onSendKey = onSendKey,
                performHaptic = ::performHaptic
            )
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
            DeviceSelectionDialog(
                discoveredDevices = discoveredDevices,
                currentDevice = currentDevice,
                hiddenIps = hiddenIps,
                activeSubnetPrefix = activeSubnetPrefix,
                activeNetworkId = activeNetworkId,
                activeSessionIps = activeSessionIps,
                unconfiguredIps = unconfiguredIps,
                isScanning = isScanning,
                onSelectDevice = onSelectDevice,
                onToggleHideDevice = onToggleHideDevice,
                onDeleteDevice = onDeleteDevice,
                onClearOtherNetworks = onClearOtherNetworks,
                onClearAllDevices = onClearAllDevices,
                onScanRequested = onScanRequested,
                onOpenManualIp = {
                    showDeviceDialog = false
                    showManualIpDialog = true
                },
                onShowSetupGuide = { dev ->
                    setupGuideDevice = dev
                },
                onDismiss = { showDeviceDialog = false }
            )
        }

        // --- TV SETUP GUIDE MODAL DIALOG ---
        if (setupGuideDevice != null) {
            TvSetupGuideDialog(
                device = setupGuideDevice!!,
                onDismiss = { setupGuideDevice = null },
                onDeviceReady = { readyDev ->
                    setupGuideDevice = null
                    onSelectDevice(readyDev)
                }
            )
        }

        // --- MANUAL IP INPUT DIALOG ---
        if (showManualIpDialog) {
            ManualIpDialog(
                onManualIpAdded = onManualIpAdded,
                onDismiss = { showManualIpDialog = false }
            )
        }

        // --- ABOUT / INFO DIALOG ---
        if (showAboutDialog) {
            AboutArgusDialog(
                onDismiss = { showAboutDialog = false }
            )
        }
    }
}
