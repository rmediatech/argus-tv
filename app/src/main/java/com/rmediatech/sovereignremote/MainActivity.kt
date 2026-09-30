package com.rmediatech.sovereignremote

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.rmediatech.sovereignremote.discovery.UniversalTvScanner
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.protocols.TvProtocolRegistry
import com.rmediatech.sovereignremote.ui.components.RemoteScreen
import com.rmediatech.sovereignremote.ui.theme.SovereignRemoteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var registry: TvProtocolRegistry
    private lateinit var scanner: UniversalTvScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = TvProtocolRegistry(applicationContext)
        scanner = UniversalTvScanner(applicationContext, registry)

        setContent {
            SovereignRemoteTheme {
                var currentDevice by remember { mutableStateOf<RemoteDevice?>(null) }
                var discoveredDevices by remember { mutableStateOf<List<RemoteDevice>>(emptyList()) }
                var isScanning by remember { mutableStateOf(false) }

                val prefs = remember { getSharedPreferences("sovereign_remote", android.content.Context.MODE_PRIVATE) }
                var hiddenIps by remember {
                    mutableStateOf(prefs.getStringSet("hidden_device_ips", emptySet()) ?: emptySet())
                }

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

                fun runDiscovery() {
                    if (isScanning) return
                    isScanning = true
                    lifecycleScope.launch {
                        try {
                            val results = scanner.discoverAll(timeoutMs = 3500)
                            discoveredDevices = results
                            val lastIp = prefs.getString("last_selected_ip", null)
                            val eligible = results.filter { !hiddenIps.contains(it.ipAddress) }
                            val preferred = eligible.find { it.ipAddress == lastIp }
                                ?: eligible.find { it.deviceType == com.rmediatech.sovereignremote.model.DeviceType.FIRE_TV }
                                ?: eligible.firstOrNull()

                            if (preferred != null && (currentDevice == null || currentDevice?.ipAddress == lastIp)) {
                                currentDevice = preferred
                                Toast.makeText(this@MainActivity, "Connected to ${preferred.name}", Toast.LENGTH_SHORT).show()
                            } else if (results.isEmpty()) {
                                Toast.makeText(this@MainActivity, "No TVs discovered on Wi-Fi", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Discovery error: ${e.message}", Toast.LENGTH_SHORT).show()
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

                    lifecycleScope.launch {
                        val result = registry.sendKey(device, key)
                        result.onFailure {
                            android.util.Log.e("MainActivity", "Command failed on ${device.ipAddress}: ${it.message}", it)
                            Toast.makeText(this@MainActivity, "Command failed: ${it.message}", Toast.LENGTH_SHORT).show()
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
                            deviceType = com.rmediatech.sovereignremote.model.DeviceType.GENERIC
                        )
                        discoveredDevices = (discoveredDevices + device).distinctBy { it.ipAddress }
                        currentDevice = device
                        Toast.makeText(this@MainActivity, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
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
