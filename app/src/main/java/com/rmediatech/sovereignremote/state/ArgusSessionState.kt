package com.rmediatech.sovereignremote.state

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import com.rmediatech.sovereignremote.discovery.UniversalTvScanner
import com.rmediatech.sovereignremote.model.*
import com.rmediatech.sovereignremote.net.DeviceLivenessTracker
import com.rmediatech.sovereignremote.net.NetworkEnvironmentManager
import com.rmediatech.sovereignremote.net.SubnetInfo
import com.rmediatech.sovereignremote.net.WakeOnLan
import com.rmediatech.sovereignremote.protocols.TvProtocolRegistry
import com.rmediatech.sovereignremote.security.SovereignSanitizer
import com.rmediatech.sovereignremote.ui.theme.ThemePreset
import kotlinx.coroutines.*

/**
 * Sovereign Argus Session State Holder:
 * One Job: Manages remote device state, per-network memory, discovery coordination,
 * and liveness heartbeats with strict OJP separation from the Android Activity shell.
 */
class ArgusSessionState(
    private val context: Context,
    private val scope: CoroutineScope,
    private val registry: TvProtocolRegistry,
    private val scanner: UniversalTvScanner
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sovereign_remote", Context.MODE_PRIVATE)

    var currentTheme by mutableStateOf(ThemePreset.fromId(prefs.getString("active_theme_preset", ThemePreset.CYBER_TACTICAL.id)))
        private set

    var activeSubnetInfo by mutableStateOf(NetworkEnvironmentManager.getActiveSubnetInfo(context))
        private set

    val currentSubnetPrefix: String
        get() = activeSubnetInfo?.subnetPrefix ?: ""

    var discoveredDevices by mutableStateOf(DeviceSerializer.deserialize(prefs.getString("saved_devices_json", null)))
        private set

    var hiddenIps by mutableStateOf(prefs.getStringSet("hidden_device_ips", emptySet()) ?: emptySet())
        private set

    var currentDevice by mutableStateOf<RemoteDevice?>(null)
        private set

    var isScanning by mutableStateOf(false)
        private set

    var livenessState by mutableStateOf(DeviceLivenessState.DISCONNECTED)
        private set

    var lastAckLatencyMs by mutableStateOf<Long?>(null)
        private set

    var isAckActive by mutableStateOf(false)
        private set

    private var livenessJob: Job? = null

    init {
        restoreInitialDevice()
        startLivenessMonitoring()
    }

    private fun restoreInitialDevice() {
        val subnetPrefix = currentSubnetPrefix
        val subnetLastIp = if (subnetPrefix.isNotBlank()) {
            prefs.getString("last_selected_ip_for_$subnetPrefix", null)
        } else null
        val lastIp = subnetLastIp ?: prefs.getString("last_selected_ip", null)

        val eligible = discoveredDevices.filter {
            !hiddenIps.contains(it.ipAddress) && (subnetPrefix.isBlank() || it.effectiveSubnet == subnetPrefix)
        }
        currentDevice = eligible.find { it.ipAddress == lastIp } ?: eligible.firstOrNull()
    }

    fun onSelectTheme(preset: ThemePreset) {
        currentTheme = preset
        prefs.edit().putString("active_theme_preset", preset.id).apply()
    }

    fun selectDevice(device: RemoteDevice) {
        currentDevice = device
        prefs.edit()
            .putString("last_selected_ip", device.ipAddress)
            .putString("last_selected_ip_for_${device.effectiveSubnet}", device.ipAddress)
            .apply()
        startLivenessMonitoring()
    }

    fun toggleHideDevice(ip: String) {
        val updated = if (hiddenIps.contains(ip)) hiddenIps - ip else hiddenIps + ip
        hiddenIps = updated
        prefs.edit().putStringSet("hidden_device_ips", updated).apply()
        if (currentDevice?.ipAddress == ip) {
            val prefix = currentSubnetPrefix
            currentDevice = discoveredDevices.find {
                !updated.contains(it.ipAddress) && (prefix.isBlank() || it.effectiveSubnet == prefix)
            }
        }
    }

    fun deleteDevice(device: RemoteDevice) {
        val updated = discoveredDevices.filter { it.ipAddress != device.ipAddress }
        discoveredDevices = updated
        prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(updated)).apply()

        if (hiddenIps.contains(device.ipAddress)) {
            val updatedHidden = hiddenIps - device.ipAddress
            hiddenIps = updatedHidden
            prefs.edit().putStringSet("hidden_device_ips", updatedHidden).apply()
        }

        if (currentDevice?.ipAddress == device.ipAddress) {
            val prefix = currentSubnetPrefix
            val nextDevice = updated.find {
                !hiddenIps.contains(it.ipAddress) && (prefix.isBlank() || it.effectiveSubnet == prefix)
            }
            currentDevice = nextDevice
            if (nextDevice != null) {
                prefs.edit().putString("last_selected_ip", nextDevice.ipAddress).apply()
            } else {
                prefs.edit().remove("last_selected_ip").apply()
            }
        }
        Toast.makeText(context, "Removed ${device.name}", Toast.LENGTH_SHORT).show()
    }

    fun clearOtherNetworks() {
        val prefix = currentSubnetPrefix
        if (prefix.isBlank()) return
        val updated = discoveredDevices.filter { it.effectiveSubnet == prefix }
        discoveredDevices = updated
        prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(updated)).apply()
        Toast.makeText(context, "Cleared other location devices", Toast.LENGTH_SHORT).show()
    }

    fun clearAllDevices() {
        discoveredDevices = emptyList()
        currentDevice = null
        prefs.edit().remove("saved_devices_json").remove("last_selected_ip").apply()
        Toast.makeText(context, "All devices cleared", Toast.LENGTH_SHORT).show()
    }

    fun onResumeNetworkCheck() {
        val fresh = NetworkEnvironmentManager.getActiveSubnetInfo(context)
        if (fresh?.subnetPrefix != activeSubnetInfo?.subnetPrefix) {
            runDiscovery()
        }
    }

    fun runDiscovery() {
        if (isScanning) return
        isScanning = true
        val freshNetInfo = NetworkEnvironmentManager.getActiveSubnetInfo(context)
        activeSubnetInfo = freshNetInfo
        val activePrefix = freshNetInfo?.subnetPrefix ?: ""
        val subnetLastIp = if (activePrefix.isNotBlank()) prefs.getString("last_selected_ip_for_$activePrefix", null) else null

        // Immediately restore saved device on this network if known, or detach foreign network device
        if (activePrefix.isNotBlank()) {
            val netEligible = discoveredDevices.filter {
                !hiddenIps.contains(it.ipAddress) && it.effectiveSubnet == activePrefix
            }
            val restored = netEligible.find { it.ipAddress == subnetLastIp } ?: netEligible.firstOrNull()
            if (restored != null) {
                currentDevice = restored
            } else if (currentDevice != null && currentDevice?.effectiveSubnet != activePrefix) {
                currentDevice = null
            }
        } else if (currentDevice != null && activePrefix.isNotBlank() && currentDevice?.effectiveSubnet != activePrefix) {
            currentDevice = null
        }

        scope.launch {
            try {
                val results = scanner.discoverAll(
                    timeoutMs = 4500,
                    onDeviceFound = { liveDev ->
                        val sanitized = liveDev.copy(
                            name = SovereignSanitizer.sanitizeDeviceName(liveDev.name),
                            modelName = SovereignSanitizer.sanitizeModelName(liveDev.modelName)
                        )
                        val updated = DeviceSerializer.merge(discoveredDevices, listOf(sanitized))
                        discoveredDevices = updated
                        prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(updated)).apply()
                        if (currentDevice == null && (activePrefix.isBlank() || sanitized.effectiveSubnet == activePrefix)) {
                            currentDevice = sanitized
                            prefs.edit()
                                .putString("last_selected_ip", sanitized.ipAddress)
                                .putString("last_selected_ip_for_${sanitized.effectiveSubnet}", sanitized.ipAddress)
                                .apply()
                        }
                    }
                )
                val sanitizedResults = results.map { dev ->
                    dev.copy(
                        name = SovereignSanitizer.sanitizeDeviceName(dev.name),
                        modelName = SovereignSanitizer.sanitizeModelName(dev.modelName)
                    )
                }
                val merged = DeviceSerializer.merge(discoveredDevices, sanitizedResults)
                discoveredDevices = merged
                prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(merged)).apply()

                val eligible = merged.filter {
                    !hiddenIps.contains(it.ipAddress) && (activePrefix.isBlank() || it.effectiveSubnet == activePrefix)
                }
                val prefIp = subnetLastIp ?: prefs.getString("last_selected_ip", null)
                val preferred = eligible.find { it.ipAddress == prefIp }
                    ?: eligible.find { it.deviceType == DeviceType.FIRE_TV || it.deviceType == DeviceType.GOOGLE_TV || it.deviceType == DeviceType.ROKU }
                    ?: eligible.firstOrNull()

                if (preferred != null && (currentDevice == null || (activePrefix.isNotBlank() && currentDevice?.effectiveSubnet != activePrefix))) {
                    currentDevice = preferred
                }
            } catch (e: Exception) {
                android.util.Log.e("ArgusSessionState", "Discovery error: ${e.message}", e)
            } finally {
                isScanning = false
            }
        }
    }

    fun addManualIp(ip: String) {
        val cleanIp = ip.trim()
        if (!SovereignSanitizer.isValidIpv4(cleanIp)) {
            Toast.makeText(context, "Invalid IPv4 address format", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            val subnet = NetworkEnvironmentManager.extractSubnetPrefix(cleanIp).takeIf { it.isNotBlank() }
            val probed = scanner.probeSpecificIp(cleanIp)
            val device = probed?.copy(subnetPrefix = subnet) ?: RemoteDevice(
                id = "manual_$cleanIp",
                name = "Smart TV ($cleanIp)",
                ipAddress = cleanIp,
                port = 8008,
                deviceType = DeviceType.GENERIC,
                subnetPrefix = subnet
            )
            val merged = DeviceSerializer.merge(discoveredDevices, listOf(device))
            discoveredDevices = merged
            prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(merged)).apply()
            selectDevice(device)
            Toast.makeText(context, "Added TV ($cleanIp)", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendKey(key: RemoteKey) {
        val device = currentDevice
        if (device == null) {
            Toast.makeText(context, "Select or scan for a TV first", Toast.LENGTH_SHORT).show()
            return
        }

        if (key == RemoteKey.POWER) {
            livenessState = DeviceLivenessState.WAKING
            val targetMac = device.macAddress ?: WakeOnLan.getMacFromArp(device.ipAddress)
            if (!targetMac.isNullOrBlank()) {
                if (device.macAddress == null) {
                    val updatedDev = device.copy(macAddress = targetMac)
                    currentDevice = updatedDev
                    val updatedList = DeviceSerializer.merge(discoveredDevices, listOf(updatedDev))
                    discoveredDevices = updatedList
                    prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(updatedList)).apply()
                }
                scope.launch(Dispatchers.IO) {
                    WakeOnLan.wake(targetMac, device.ipAddress, context)
                }
            }
        }

        val t0 = System.currentTimeMillis()
        scope.launch {
            val result = registry.sendKey(device, key)
            val elapsed = (System.currentTimeMillis() - t0).coerceAtLeast(1)
            if (result.isSuccess) {
                lastAckLatencyMs = elapsed
                isAckActive = true
                if (key != RemoteKey.POWER) {
                    livenessState = DeviceLivenessState.ONLINE
                }
                scope.launch {
                    delay(350)
                    isAckActive = false
                }
            } else {
                lastAckLatencyMs = -1L
                isAckActive = true
                scope.launch {
                    delay(800)
                    isAckActive = false
                }
            }

            if (key == RemoteKey.POWER) {
                scope.launch {
                    var detectedOnline = false
                    // Phase 1: Fast-react burst (12 probes with 120ms delay & 100ms timeout ~ 2.6s)
                    for (i in 1..12) {
                        delay(120)
                        val state = DeviceLivenessTracker.probeLiveness(device, 100)
                        if (state == DeviceLivenessState.ONLINE) {
                            livenessState = DeviceLivenessState.ONLINE
                            detectedOnline = true
                            break
                        }
                    }
                    // Phase 2: Steady polling (8 probes with 350ms delay & 200ms timeout ~ 4.4s)
                    if (!detectedOnline) {
                        for (i in 1..8) {
                            delay(350)
                            val state = DeviceLivenessTracker.probeLiveness(device, 200)
                            if (state == DeviceLivenessState.ONLINE) {
                                livenessState = DeviceLivenessState.ONLINE
                                detectedOnline = true
                                break
                            }
                        }
                    }
                    if (!detectedOnline) {
                        livenessState = DeviceLivenessTracker.probeLiveness(device, 250)
                    }
                }
            }
        }
    }

    private fun startLivenessMonitoring() {
        livenessJob?.cancel()
        val dev = currentDevice ?: run {
            livenessState = DeviceLivenessState.DISCONNECTED
            return
        }
        livenessJob = scope.launch {
            while (isActive) {
                if (livenessState != DeviceLivenessState.WAKING) {
                    livenessState = DeviceLivenessTracker.probeLiveness(dev, 250)
                }
                delay(4000)
            }
        }
    }
}
