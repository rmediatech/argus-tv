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

    val currentNetworkId: String
        get() = activeSubnetInfo?.networkId ?: (if (currentSubnetPrefix.isNotBlank()) "net_subnet_$currentSubnetPrefix" else "")

    var discoveredDevices by mutableStateOf(DeviceSerializer.deserialize(prefs.getString("saved_devices_json", null)))
        private set

    var hiddenIps by mutableStateOf(prefs.getStringSet("hidden_device_ips", emptySet()) ?: emptySet())
        private set

    var ignoredIps by mutableStateOf(prefs.getStringSet("ignored_device_ips", emptySet()) ?: emptySet())
        private set

    var activeSessionIps by mutableStateOf<Set<String>>(emptySet())
        private set

    var unconfiguredIps by mutableStateOf<Set<String>>(emptySet())
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
        cleanStaleDevices()
        restoreInitialDevice()
        startLivenessMonitoring()
        refreshUnconfiguredDevices()
    }

    private fun cleanStaleDevices() {
        val cleaned = discoveredDevices.filterNot {
            (it.deviceType == DeviceType.GENERIC && (it.port == 8080 || it.port == 80 || it.port == 60000)) ||
            (it.deviceType == DeviceType.SONY_BRAVIA && it.ipAddress.endsWith(".1") && it.port == 80) ||
            ignoredIps.contains(it.ipAddress)
        }
        if (cleaned.size != discoveredDevices.size) {
            discoveredDevices = cleaned
            prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(cleaned)).apply()
        }
    }

    private fun restoreInitialDevice() {
        val netId = currentNetworkId
        val subnetPrefix = currentSubnetPrefix
        val netLastIp = if (netId.isNotBlank()) {
            prefs.getString("last_selected_ip_for_$netId", null)
                ?: if (subnetPrefix.isNotBlank()) prefs.getString("last_selected_ip_for_$subnetPrefix", null) else null
        } else null
        val lastIp = netLastIp ?: prefs.getString("last_selected_ip", null)

        val eligible = discoveredDevices.filter {
            !hiddenIps.contains(it.ipAddress) && !ignoredIps.contains(it.ipAddress) && 
            SubnetInfo.isDeviceOnNetwork(it, netId, subnetPrefix)
        }
        currentDevice = eligible.find { it.ipAddress == lastIp } ?: eligible.firstOrNull()
    }

    fun onSelectTheme(preset: ThemePreset) {
        currentTheme = preset
        prefs.edit().putString("active_theme_preset", preset.id).apply()
    }

    fun selectDevice(device: RemoteDevice) {
        currentDevice = device
        com.rmediatech.sovereignremote.logging.SovereignLogger.i(
            com.rmediatech.sovereignremote.logging.SovereignLogger.Category.LIFECYCLE,
            "Device selected: [${device.deviceType}] ${device.name} (${device.ipAddress}:${device.port})"
        )
        prefs.edit()
            .putString("last_selected_ip", device.ipAddress)
            .putString("last_selected_ip_for_${device.effectiveNetworkId}", device.ipAddress)
            .putString("last_selected_ip_for_${device.effectiveSubnet}", device.ipAddress)
            .apply()
        startLivenessMonitoring()
    }

    fun toggleHideDevice(ip: String) {
        val updated = if (hiddenIps.contains(ip)) hiddenIps - ip else hiddenIps + ip
        hiddenIps = updated
        prefs.edit().putStringSet("hidden_device_ips", updated).apply()
        if (currentDevice?.ipAddress == ip) {
            val netId = currentNetworkId
            val subnetPrefix = currentSubnetPrefix
            currentDevice = discoveredDevices.find {
                !updated.contains(it.ipAddress) && !ignoredIps.contains(it.ipAddress) && 
                (netId.isBlank() || it.effectiveNetworkId == netId || (subnetPrefix.isNotBlank() && it.effectiveSubnet == subnetPrefix))
            }
            currentDevice?.let { next ->
                prefs.edit().putString("last_selected_ip", next.ipAddress).apply()
            }
        }
    }

    fun deleteDevice(device: RemoteDevice) {
        val updated = discoveredDevices.filter { it.ipAddress != device.ipAddress }
        discoveredDevices = updated
        prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(updated)).apply()

        // Permanently blacklist so background scan won't resurrect this unwanted device
        val updatedIgnored = ignoredIps + device.ipAddress
        ignoredIps = updatedIgnored
        prefs.edit().putStringSet("ignored_device_ips", updatedIgnored).apply()

        if (hiddenIps.contains(device.ipAddress)) {
            val updatedHidden = hiddenIps - device.ipAddress
            hiddenIps = updatedHidden
            prefs.edit().putStringSet("hidden_device_ips", updatedHidden).apply()
        }

        if (currentDevice?.ipAddress == device.ipAddress) {
            val netId = currentNetworkId
            val subnet = currentSubnetPrefix
            val nextDevice = updated.find {
                !hiddenIps.contains(it.ipAddress) && !updatedIgnored.contains(it.ipAddress) && 
                (netId.isBlank() || it.effectiveNetworkId == netId || (subnet.isNotBlank() && it.effectiveSubnet == subnet))
            }
            currentDevice = nextDevice
            if (nextDevice != null) {
                prefs.edit().putString("last_selected_ip", nextDevice.ipAddress).apply()
            } else {
                prefs.edit().remove("last_selected_ip").apply()
            }
        }
        Toast.makeText(context, "Removed & forgotten ${device.name}", Toast.LENGTH_SHORT).show()
    }

    fun clearOtherNetworks() {
        val netId = currentNetworkId
        if (netId.isBlank()) return
        val updated = discoveredDevices.filter { it.effectiveNetworkId == netId }
        discoveredDevices = updated
        prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(updated)).apply()
        Toast.makeText(context, "Cleared other location devices", Toast.LENGTH_SHORT).show()
    }

    fun clearAllDevices() {
        discoveredDevices = emptyList()
        currentDevice = null
        ignoredIps = emptySet()
        hiddenIps = emptySet()
        prefs.edit()
            .remove("saved_devices_json")
            .remove("last_selected_ip")
            .remove("ignored_device_ips")
            .remove("hidden_device_ips")
            .apply()
        Toast.makeText(context, "All devices cleared", Toast.LENGTH_SHORT).show()
    }

    fun onResumeNetworkCheck() {
        NetworkEnvironmentManager.clearGatewayHardwareCache()
        registry.resetConnections()
        val fresh = NetworkEnvironmentManager.getActiveSubnetInfo(context)
        if (fresh?.networkId != activeSubnetInfo?.networkId) {
            runDiscovery()
        } else {
            startLivenessMonitoring()
        }
    }

    fun runDiscovery() {
        if (isScanning) return
        isScanning = true
        val freshNetInfo = NetworkEnvironmentManager.getActiveSubnetInfo(context)
        activeSubnetInfo = freshNetInfo
        val activeNetId = freshNetInfo?.networkId ?: ""
        val activePrefix = freshNetInfo?.subnetPrefix ?: ""
        com.rmediatech.sovereignremote.logging.SovereignLogger.i(
            com.rmediatech.sovereignremote.logging.SovereignLogger.Category.DISCOVERY,
            "runDiscovery() triggered (activeNetId=$activeNetId, subnet=$activePrefix)"
        )
        val netLastIp = if (activeNetId.isNotBlank()) {
            prefs.getString("last_selected_ip_for_$activeNetId", null)
                ?: if (activePrefix.isNotBlank()) prefs.getString("last_selected_ip_for_$activePrefix", null) else null
        } else null

        // Immediately restore saved device on this network if known, or detach foreign network device
        if (activeNetId.isNotBlank()) {
            val netEligible = discoveredDevices.filter {
                !hiddenIps.contains(it.ipAddress) && SubnetInfo.isDeviceOnNetwork(it, activeNetId, activePrefix)
            }
            val restored = netEligible.find { it.ipAddress == netLastIp } ?: netEligible.firstOrNull()
            if (restored != null) {
                currentDevice = restored
            } else if (currentDevice != null && !SubnetInfo.isDeviceOnNetwork(currentDevice!!, activeNetId, activePrefix)) {
                currentDevice = null
            }
        } else if (currentDevice != null && activeNetId.isNotBlank() && !SubnetInfo.isDeviceOnNetwork(currentDevice!!, activeNetId, activePrefix)) {
            currentDevice = null
        }

        scope.launch {
            try {
                // Background hardware gateway resolution if not already cached
                val gwIp = freshNetInfo?.gatewayIp
                if (!gwIp.isNullOrBlank() && freshNetInfo?.gatewayHardwareId == null) {
                    val probedHw = withContext(Dispatchers.IO) {
                        NetworkEnvironmentManager.probeGatewayHardwareId(gwIp, forceFresh = true)
                    }
                    if (probedHw != null) {
                        NetworkEnvironmentManager.recordGatewayHardwareId(context, gwIp, probedHw)
                        val updatedNet = NetworkEnvironmentManager.getActiveSubnetInfo(context)
                        if (updatedNet != null) {
                            activeSubnetInfo = updatedNet
                        }
                    }
                }

                activeSessionIps = emptySet()
                val currentEffectiveNetId = activeSubnetInfo?.networkId ?: activeNetId
                if (activePrefix.isNotBlank() && currentEffectiveNetId.startsWith("net_hw_")) {
                    // Only heal legacy un-fingerprinted devices (net_subnet_). Never cross-contaminate existing hardware-anchored devices!
                    val healed = discoveredDevices.map { dev ->
                        if (dev.networkId?.startsWith("net_subnet_") == true && dev.effectiveSubnet == activePrefix) {
                            dev.copy(networkId = currentEffectiveNetId)
                        } else dev
                    }
                    if (healed != discoveredDevices) {
                        discoveredDevices = healed
                        prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(healed)).apply()
                    }
                }

                val results = scanner.discoverAll(
                    timeoutMs = 3500,
                    onDeviceFound = { liveDev ->
                        if (ignoredIps.contains(liveDev.ipAddress)) return@discoverAll
                        activeSessionIps = activeSessionIps + liveDev.ipAddress
                        val targetNetId = activeSubnetInfo?.networkId ?: currentEffectiveNetId
                        val sanitized = liveDev.copy(
                            name = SovereignSanitizer.sanitizeDeviceName(liveDev.name),
                            modelName = SovereignSanitizer.sanitizeModelName(liveDev.modelName),
                            networkId = targetNetId.takeIf { it.isNotBlank() } ?: liveDev.networkId
                        )
                        val updated = DeviceSerializer.merge(discoveredDevices, listOf(sanitized))
                        discoveredDevices = updated
                        prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(updated)).apply()
                        if (currentDevice == null && !hiddenIps.contains(sanitized.ipAddress) && 
                            (targetNetId.isBlank() || sanitized.effectiveNetworkId == targetNetId || (activePrefix.isNotBlank() && sanitized.effectiveSubnet == activePrefix))) {
                            currentDevice = sanitized
                            prefs.edit()
                                .putString("last_selected_ip", sanitized.ipAddress)
                                .putString("last_selected_ip_for_${sanitized.effectiveNetworkId}", sanitized.ipAddress)
                                .apply()
                        }
                    }
                )
                val activeHwNetId = activeSubnetInfo?.networkId ?: currentEffectiveNetId
                val sanitizedResults = results.filterNot { ignoredIps.contains(it.ipAddress) }.map { dev ->
                    dev.copy(
                        name = SovereignSanitizer.sanitizeDeviceName(dev.name),
                        modelName = SovereignSanitizer.sanitizeModelName(dev.modelName),
                        networkId = activeHwNetId.takeIf { it.isNotBlank() } ?: dev.networkId
                    )
                }
                activeSessionIps = activeSessionIps + sanitizedResults.map { it.ipAddress }
                val merged = DeviceSerializer.merge(discoveredDevices, sanitizedResults)
                discoveredDevices = merged
                prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(merged)).apply()

                val eligible = merged.filter {
                    !hiddenIps.contains(it.ipAddress) && !ignoredIps.contains(it.ipAddress) && 
                    SubnetInfo.isDeviceOnNetwork(it, activeHwNetId, activePrefix)
                }
                // Under-the-waterline invariant: Prioritize devices physically confirmed online in this scan session
                val onlineConfirmed = eligible.filter { activeSessionIps.contains(it.ipAddress) }
                val candidates = if (onlineConfirmed.isNotEmpty()) onlineConfirmed else eligible
                val prefIp = netLastIp ?: prefs.getString("last_selected_ip", null)
                val preferred = candidates.find { it.ipAddress == prefIp }
                    ?: candidates.find { it.deviceType == DeviceType.FIRE_TV || it.deviceType == DeviceType.GOOGLE_TV || it.deviceType == DeviceType.ROKU }
                    ?: candidates.firstOrNull()

                if (preferred != null && (
                    currentDevice == null || 
                    (activeHwNetId.isNotBlank() && !SubnetInfo.isDeviceOnNetwork(currentDevice!!, activeHwNetId, activePrefix)) ||
                    (activeSessionIps.isNotEmpty() && currentDevice != null && !activeSessionIps.contains(currentDevice?.ipAddress))
                )) {
                    currentDevice = preferred
                }
            } catch (e: Exception) {
                android.util.Log.e("ArgusSessionState", "Discovery error: ${e.message}", e)
            } finally {
                isScanning = false
                refreshUnconfiguredDevices()
            }
        }
    }

    fun refreshUnconfiguredDevices() {
        scope.launch(Dispatchers.IO) {
            val unconfigured = mutableSetOf<String>()
            for (dev in discoveredDevices) {
                if (dev.deviceType == DeviceType.FIRE_TV) {
                    if (!DeviceLivenessTracker.isPortOpen(dev.ipAddress, 5555, 150)) {
                        unconfigured.add(dev.ipAddress)
                    }
                } else if (dev.deviceType == DeviceType.VIZIO_SMARTCAST) {
                    val token = prefs.getString("vizio_auth_${dev.ipAddress}", null)
                    if (token.isNullOrBlank()) {
                        unconfigured.add(dev.ipAddress)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                unconfiguredIps = unconfigured
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
            val netInfo = NetworkEnvironmentManager.getActiveSubnetInfo(context)
            val subnet = NetworkEnvironmentManager.extractSubnetPrefix(cleanIp).takeIf { it.isNotBlank() }
            val probed = scanner.probeSpecificIp(cleanIp)
            val device = probed?.copy(
                subnetPrefix = subnet ?: probed.subnetPrefix,
                networkId = netInfo?.networkId ?: probed.networkId
            ) ?: RemoteDevice(
                id = "manual_$cleanIp",
                name = "Smart TV ($cleanIp)",
                ipAddress = cleanIp,
                port = 8008,
                deviceType = DeviceType.GENERIC,
                subnetPrefix = subnet,
                networkId = netInfo?.networkId
            )
            val merged = DeviceSerializer.merge(discoveredDevices, listOf(device))
            discoveredDevices = merged
            prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(merged)).apply()
            selectDevice(device)
            Toast.makeText(context, "Added TV ($cleanIp)", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendKey(key: RemoteKey) {
        var device = currentDevice
        if (device == null) {
            Toast.makeText(context, "Select or scan for a TV first", Toast.LENGTH_SHORT).show()
            return
        }

        // Auto-heal Fire TV device type and port if misassigned
        val isAmazon = device.name.contains("Fire", ignoreCase = true) || 
            device.modelName?.contains("Fire", ignoreCase = true) == true ||
            com.rmediatech.sovereignremote.discovery.SmartTvClassifier.inferBrandFromMac(device.macAddress) == DeviceType.FIRE_TV
        if (isAmazon && (device.deviceType != DeviceType.FIRE_TV || device.port != 5555)) {
            device = device.copy(
                deviceType = DeviceType.FIRE_TV,
                port = 5555,
                modelName = if (device.modelName.isNullOrBlank() || device.modelName == "Google TV") "Amazon Fire TV" else device.modelName
            )
            currentDevice = device
            val updated = DeviceSerializer.merge(discoveredDevices, listOf(device))
            discoveredDevices = updated
            prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(updated)).apply()
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
        com.rmediatech.sovereignremote.logging.SovereignLogger.i(
            com.rmediatech.sovereignremote.logging.SovereignLogger.Category.PROTOCOL,
            "sendKey($key) -> ${device.name} (${device.ipAddress}:${device.port})"
        )
        scope.launch {
            val dispatch = registry.sendKeyWithAutoPromotion(device, key)
            if (dispatch.isSuccess) {
                lastAckLatencyMs = dispatch.elapsedMs
                isAckActive = true
                com.rmediatech.sovereignremote.logging.SovereignLogger.i(
                    com.rmediatech.sovereignremote.logging.SovereignLogger.Category.PROTOCOL,
                    "sendKey($key) ACK received in ${dispatch.elapsedMs}ms from ${device.ipAddress}"
                )
                if (key != RemoteKey.POWER) {
                    livenessState = DeviceLivenessState.ONLINE
                }

                // If device was auto-promoted to a new winning protocol, update state & persistence
                val promoted = dispatch.promotedDevice
                if (promoted != null && (promoted.deviceType != device.deviceType || promoted.port != device.port)) {
                    currentDevice = promoted
                    val updated = DeviceSerializer.merge(discoveredDevices, listOf(promoted))
                    discoveredDevices = updated
                    prefs.edit()
                        .putString("saved_devices_json", DeviceSerializer.serialize(updated))
                        .putString("last_selected_ip", promoted.ipAddress)
                        .putString("last_selected_ip_for_${promoted.effectiveNetworkId}", promoted.ipAddress)
                        .apply()
                    Toast.makeText(context, "Promoted to ${promoted.deviceType.displayName}!", Toast.LENGTH_SHORT).show()
                }

                scope.launch {
                    delay(350)
                    isAckActive = false
                }
            } else {
                lastAckLatencyMs = -1L
                isAckActive = true
                livenessState = DeviceLivenessState.DISCONNECTED
                val errMsg = dispatch.error?.message.orEmpty().lowercase()
                com.rmediatech.sovereignremote.logging.SovereignLogger.w(
                    com.rmediatech.sovereignremote.logging.SovereignLogger.Category.PROTOCOL,
                    "sendKey($key) FAILED on ${device.ipAddress}: ${dispatch.error?.message}"
                )
                if (errMsg.contains("unauthorized") || errMsg.contains("permission") || errMsg.contains("denied")) {
                    Toast.makeText(context, "Pairing needed: Look at your TV screen and select 'Always Allow'", Toast.LENGTH_LONG).show()
                }
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
