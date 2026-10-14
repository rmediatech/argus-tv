package com.rmediatech.sovereignremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import com.rmediatech.sovereignremote.discovery.UniversalTvScanner
import com.rmediatech.sovereignremote.protocols.TvProtocolRegistry
import com.rmediatech.sovereignremote.state.ArgusSessionState
import com.rmediatech.sovereignremote.ui.components.RemoteScreen
import com.rmediatech.sovereignremote.ui.theme.SovereignRemoteTheme

/**
 * Sovereign Argus Remote Entry Point:
 * One Job: Android ComponentActivity lifecycle host and Compose root.
 * Delegates all state, network multi-location context, and device actions to ArgusSessionState.
 */
class MainActivity : ComponentActivity() {

    private lateinit var sessionState: ArgusSessionState

    override fun onResume() {
        super.onResume()
        if (::sessionState.isInitialized) {
            sessionState.onResumeNetworkCheck()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val registry = TvProtocolRegistry(applicationContext)
        val scanner = UniversalTvScanner(applicationContext, registry)
        sessionState = ArgusSessionState(applicationContext, lifecycleScope, registry, scanner)

        setContent {
            SovereignRemoteTheme(preset = sessionState.currentTheme) {
                LaunchedEffect(Unit) {
                    sessionState.runDiscovery()
                }

                RemoteScreen(
                    currentDevice = sessionState.currentDevice,
                    discoveredDevices = sessionState.discoveredDevices,
                    hiddenIps = sessionState.hiddenIps,
                    activeSubnetPrefix = sessionState.currentSubnetPrefix.takeIf { it.isNotBlank() },
                    activeNetworkId = sessionState.currentNetworkId.takeIf { it.isNotBlank() },
                    isScanning = sessionState.isScanning,
                    livenessState = sessionState.livenessState,
                    lastAckLatencyMs = sessionState.lastAckLatencyMs,
                    isAckActive = sessionState.isAckActive,
                    currentTheme = sessionState.currentTheme,
                    onSelectTheme = { sessionState.onSelectTheme(it) },
                    onScanRequested = { sessionState.runDiscovery() },
                    onSelectDevice = { sessionState.selectDevice(it) },
                    onToggleHideDevice = { sessionState.toggleHideDevice(it) },
                    onDeleteDevice = { sessionState.deleteDevice(it) },
                    onClearOtherNetworks = { sessionState.clearOtherNetworks() },
                    onClearAllDevices = { sessionState.clearAllDevices() },
                    onManualIpAdded = { sessionState.addManualIp(it) },
                    onSendKey = { sessionState.sendKey(it) }
                )
            }
        }
    }
}
