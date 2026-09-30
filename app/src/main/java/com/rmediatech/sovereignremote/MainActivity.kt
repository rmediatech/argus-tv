package com.rmediatech.sovereignremote

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.rmediatech.sovereignremote.discovery.UniversalTvScanner
import com.rmediatech.sovereignremote.model.DeviceLivenessState
import com.rmediatech.sovereignremote.model.DeviceSerializer
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.net.DeviceLivenessTracker
import com.rmediatech.sovereignremote.protocols.TvProtocolRegistry
import com.rmediatech.sovereignremote.ui.components.RemoteScreen
import com.rmediatech.sovereignremote.ui.theme.SovereignRemoteTheme
import com.rmediatech.sovereignremote.ui.theme.ThemePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var registry: TvProtocolRegistry
    private lateinit var scanner: UniversalTvScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = TvProtocolRegistry(applicationContext)
        scanner = UniversalTvScanner(applicationContext, registry)

        setContent {
            val prefs = remember { getSharedPreferences("sovereign_remote", android.content.Context.MODE_PRIVATE) }
            val savedThemeId = prefs.getString("active_theme_preset", ThemePreset.CYBER_TACTICAL.id)
            var currentTheme by remember { mutableStateOf(ThemePreset.fromId(savedThemeId)) }

            fun onSelectTheme(preset: ThemePreset) {
                currentTheme = preset
                prefs.edit().putString("active_theme_preset", preset.id).apply()
            }

            SovereignRemoteTheme(preset = currentTheme) {
                val initialDevices = remember { DeviceSerializer.deserialize(prefs.getString("saved_devices_json", null)) }
                var discoveredDevices by remember { mutableStateOf(initialDevices) }
                var isScanning by remember { mutableStateOf(false) }

                var hiddenIps by remember {
                    mutableStateOf(prefs.getStringSet("hidden_device_ips", emptySet()) ?: emptySet())
                }

                val lastIp = prefs.getString("last_selected_ip", null)
                var currentDevice by remember {
                    mutableStateOf(
                        initialDevices.find { it.ipAddress == lastIp }
                            ?: initialDevices.find { !hiddenIps.contains(it.ipAddress) }
                            ?: initialDevices.firstOrNull()
                    )
                }

                var livenessState by remember { mutableStateOf(DeviceLivenessState.DISCONNECTED) }
                var lastAckLatencyMs by remember { mutableStateOf<Long?>(null) }
                var isAckActive by remember { mutableStateOf(false) }

                fun toggleHideDevice(ip: String) {
                    val updated = if (hiddenIps.contains(ip)) hiddenIps - ip else hiddenIps + ip
                    hiddenIps = updated
                    prefs.edit().putStringSet("hidden_device_ips", updated).apply()
                    if (currentDevice?.ipAddress == ip) {
                        currentDevice = discoveredDevices.find { !updated.contains(it.ipAddress) }
                    }
                }

                fun selectDevice(device: RemoteDevice) {
                    currentDevice = device
                    prefs.edit().putString("last_selected_ip", device.ipAddress).apply()
                }

                // Periodic Liveness Heartbeat Probe
                LaunchedEffect(currentDevice) {
                    val dev = currentDevice
                    if (dev == null) {
                        livenessState = DeviceLivenessState.DISCONNECTED
                        return@LaunchedEffect
                    }
                    while (isActive) {
                        if (livenessState != DeviceLivenessState.WAKING) {
                            val state = DeviceLivenessTracker.probeLiveness(dev, 250)
                            livenessState = state
                        }
                        delay(4000)
                    }
                }

                fun runDiscovery() {
                    if (isScanning) return
                    isScanning = true
                    lifecycleScope.launch {
                        try {
                            val results = scanner.discoverAll(timeoutMs = 3500)
                            val merged = DeviceSerializer.merge(discoveredDevices, results)
                            discoveredDevices = merged
                            prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(merged)).apply()

                            val prefIp = prefs.getString("last_selected_ip", null)
                            val eligible = merged.filter { !hiddenIps.contains(it.ipAddress) }
                            val preferred = eligible.find { it.ipAddress == prefIp }
                                ?: eligible.find { it.deviceType == DeviceType.FIRE_TV }
                                ?: eligible.firstOrNull()

                            if (preferred != null && (currentDevice == null || currentDevice?.ipAddress == prefIp)) {
                                currentDevice = preferred
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Discovery error: ${e.message}", e)
                        } finally {
                            isScanning = false
                        }
                    }
                }

                fun sendKey(key: RemoteKey) {
                    val device = currentDevice
                    if (device == null) {
                        Toast.makeText(this@MainActivity, "Select or scan for a TV first", Toast.LENGTH_SHORT).show()
                        return
                    }

                    if (key == RemoteKey.POWER) {
                        livenessState = DeviceLivenessState.WAKING
                        // Send Wake-on-LAN if MAC address is known
                        device.macAddress?.let { mac ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                com.rmediatech.sovereignremote.net.WakeOnLan.wake(mac, device.ipAddress)
                            }
                        }
                    }

                    val t0 = System.currentTimeMillis()
                    lifecycleScope.launch {
                        val result = registry.sendKey(device, key)
                        val elapsed = (System.currentTimeMillis() - t0).coerceAtLeast(1)
                        if (result.isSuccess) {
                            lastAckLatencyMs = elapsed
                            isAckActive = true
                            if (key != RemoteKey.POWER) {
                                livenessState = DeviceLivenessState.ONLINE
                            }
                            launch {
                                delay(350)
                                isAckActive = false
                            }
                        } else {
                            lastAckLatencyMs = -1L
                            isAckActive = true
                            launch {
                                delay(800)
                                isAckActive = false
                            }
                            result.onFailure {
                                android.util.Log.e("MainActivity", "Command failed on ${device.ipAddress}: ${it.message}", it)
                            }
                        }

                        // For POWER key, actively fast-poll device port to detect wake-up
                        if (key == RemoteKey.POWER) {
                            launch {
                                var detectedOnline = false
                                for (i in 1..16) {
                                    delay(600)
                                    val state = DeviceLivenessTracker.probeLiveness(device, 250)
                                    if (state == DeviceLivenessState.ONLINE) {
                                        livenessState = DeviceLivenessState.ONLINE
                                        detectedOnline = true
                                        break
                                    }
                                }
                                if (!detectedOnline) {
                                    livenessState = DeviceLivenessTracker.probeLiveness(device, 300)
                                }
                            }
                        }
                    }
                }

                fun addManualIp(ip: String) {
                    lifecycleScope.launch {
                        val probed = scanner.probeSpecificIp(ip)
                        val device = probed ?: RemoteDevice(
                            id = "manual_$ip",
                            name = "Smart TV ($ip)",
                            ipAddress = ip,
                            port = 8008,
                            deviceType = DeviceType.GENERIC
                        )
                        val merged = DeviceSerializer.merge(discoveredDevices, listOf(device))
                        discoveredDevices = merged
                        prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(merged)).apply()
                        currentDevice = device
                        prefs.edit().putString("last_selected_ip", device.ipAddress).apply()
                    }
                }

                // Initial scan when app opens
                LaunchedEffect(Unit) {
                    runDiscovery()
                }

                RemoteScreen(
                    currentDevice = currentDevice,
                    discoveredDevices = discoveredDevices,
                    hiddenIps = hiddenIps,
                    isScanning = isScanning,
                    livenessState = livenessState,
                    lastAckLatencyMs = lastAckLatencyMs,
                    isAckActive = isAckActive,
                    currentTheme = currentTheme,
                    onSelectTheme = { onSelectTheme(it) },
                    onScanRequested = { runDiscovery() },
                    onSelectDevice = { selectDevice(it) },
                    onToggleHideDevice = { toggleHideDevice(it) },
                    onManualIpAdded = { addManualIp(it) },
                    onSendKey = { sendKey(it) }
                )
            }
        }
    }
}
