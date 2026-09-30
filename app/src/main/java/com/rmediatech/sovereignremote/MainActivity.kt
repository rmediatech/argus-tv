package com.rmediatech.sovereignremote

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.rmediatech.sovereignremote.discovery.UniversalTvScanner
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.protocols.TvProtocolRegistry
import com.rmediatech.sovereignremote.ui.components.RemoteScreen
import com.rmediatech.sovereignremote.ui.theme.SovereignRemoteTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var registry: TvProtocolRegistry
    private lateinit var scanner: UniversalTvScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry = TvProtocolRegistry(applicationContext)
        scanner = UniversalTvScanner(applicationContext, registry)

        setContent {
            SovereignRemoteTheme {
                val prefs = remember { getSharedPreferences("sovereign_remote", android.content.Context.MODE_PRIVATE) }
                val initialDevices = remember { loadSavedDevices(prefs) }
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
                            val merged = mergeDevices(discoveredDevices, results)
                            discoveredDevices = merged
                            saveDevices(prefs, merged)

                            val prefIp = prefs.getString("last_selected_ip", null)
                            val eligible = merged.filter { !hiddenIps.contains(it.ipAddress) }
                            val preferred = eligible.find { it.ipAddress == prefIp }
                                ?: eligible.find { it.deviceType == DeviceType.FIRE_TV }
                                ?: eligible.firstOrNull()

                            if (preferred != null && (currentDevice == null || currentDevice?.ipAddress == prefIp)) {
                                currentDevice = preferred
                                Toast.makeText(this@MainActivity, "Connected to ${preferred.name}", Toast.LENGTH_SHORT).show()
                            } else if (results.isEmpty() && merged.isEmpty()) {
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

                    if (key == RemoteKey.POWER) {
                        Toast.makeText(this@MainActivity, "Powering ${device.name}...", Toast.LENGTH_SHORT).show()
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
                            deviceType = DeviceType.GENERIC
                        )
                        val merged = mergeDevices(discoveredDevices, listOf(device))
                        discoveredDevices = merged
                        saveDevices(prefs, merged)
                        currentDevice = device
                        prefs.edit().putString("last_selected_ip", device.ipAddress).apply()
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

    private fun mergeDevices(existing: List<RemoteDevice>, fresh: List<RemoteDevice>): List<RemoteDevice> {
        val map = existing.associateBy { it.ipAddress }.toMutableMap()
        for (f in fresh) {
            val old = map[f.ipAddress]
            if (old != null) {
                map[f.ipAddress] = f.copy(
                    macAddress = f.macAddress ?: old.macAddress,
                    modelName = f.modelName ?: old.modelName
                )
            } else {
                map[f.ipAddress] = f
            }
        }
        return map.values.toList()
    }

    private fun saveDevices(prefs: SharedPreferences, devices: List<RemoteDevice>) {
        val arr = JSONArray()
        for (d in devices) {
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("name", d.name)
            obj.put("ipAddress", d.ipAddress)
            obj.put("port", d.port)
            obj.put("deviceType", d.deviceType.name)
            obj.put("modelName", d.modelName ?: "")
            obj.put("isEthernet", d.isEthernet)
            obj.put("macAddress", d.macAddress ?: "")
            arr.put(obj)
        }
        prefs.edit().putString("saved_devices_json", arr.toString()).apply()
    }

    private fun loadSavedDevices(prefs: SharedPreferences): List<RemoteDevice> {
        val jsonStr = prefs.getString("saved_devices_json", null) ?: return emptyList()
        val list = mutableListOf<RemoteDevice>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    RemoteDevice(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        ipAddress = obj.getString("ipAddress"),
                        port = obj.getInt("port"),
                        deviceType = DeviceType.valueOf(obj.getString("deviceType")),
                        modelName = obj.optString("modelName").takeIf { it.isNotBlank() },
                        isEthernet = obj.optBoolean("isEthernet", false),
                        macAddress = obj.optString("macAddress").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
