package com.rmediatech.sovereignremote.state

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.*
import com.rmediatech.sovereignremote.discovery.UniversalTvScanner
import com.rmediatech.sovereignremote.logging.SovereignLogger
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
 * One Job: Manages observable Compose state, per-network memory, discovery coordination,
 * and liveness heartbeats with strict OJP separation from the Android Activity shell.
 */
class ArgusSessionState(
    private val context: Context,
    private val scope: CoroutineScope,
    private val registry: TvProtocolRegistry,
    private val scanner: UniversalTvScanner
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sovereign_remote", Context.MODE_PRIVATE)

    var currentTheme by mutableStateOf(ThemePreset.fromId(prefs.getString("active_theme_preset", ThemePreset.CYBER_TACTICAL.id))); private set
    var activeSubnetInfo by mutableStateOf(NetworkEnvironmentManager.getActiveSubnetInfo(context)); private set
    val currentSubnetPrefix: String get() = activeSubnetInfo?.subnetPrefix ?: ""
    val currentNetworkId: String get() = activeSubnetInfo?.networkId ?: (if (currentSubnetPrefix.isNotBlank()) "net_subnet_$currentSubnetPrefix" else "")

    var discoveredDevices by mutableStateOf(DeviceSerializer.deserialize(prefs.getString("saved_devices_json", null))); private set
    var hiddenIps by mutableStateOf(prefs.getStringSet("hidden_device_ips", emptySet()) ?: emptySet()); private set
    var ignoredIps by mutableStateOf(prefs.getStringSet("ignored_device_ips", emptySet()) ?: emptySet()); private set
    var activeSessionIps by mutableStateOf<Set<String>>(emptySet()); private set
    var unconfiguredIps by mutableStateOf<Set<String>>(emptySet()); private set
    var currentDevice by mutableStateOf<RemoteDevice?>(null); private set
    var isScanning by mutableStateOf(false); private set
    var livenessState by mutableStateOf(DeviceLivenessState.DISCONNECTED); private set
    var lastAckLatencyMs by mutableStateOf<Long?>(null); private set
    var isAckActive by mutableStateOf(false); private set
    private var livenessJob: Job? = null

    init {
        val cleaned = ArgusSessionHelper.cleanStaleDevices(discoveredDevices, ignoredIps)
        if (cleaned.size != discoveredDevices.size) { discoveredDevices = cleaned; persistDevices(cleaned) }
        restoreInitialDevice()
        startLivenessMonitoring()
        refreshUnconfiguredDevices()
    }

    private fun persistDevices(devices: List<RemoteDevice>) {
        prefs.edit().putString("saved_devices_json", DeviceSerializer.serialize(devices)).apply()
    }

    private fun restoreInitialDevice() {
        val (netId, subnetPrefix) = currentNetworkId to currentSubnetPrefix
        val lastIp = ArgusSessionHelper.resolveLastNetworkIp(prefs, netId, subnetPrefix)
        val eligible = discoveredDevices.filter {
            !hiddenIps.contains(it.ipAddress) && !ignoredIps.contains(it.ipAddress) && SubnetInfo.isDeviceOnNetwork(it, netId, subnetPrefix)
        }
        currentDevice = eligible.find { it.ipAddress == lastIp } ?: eligible.firstOrNull()
    }

    fun onSelectTheme(preset: ThemePreset) {
        currentTheme = preset
        prefs.edit().putString("active_theme_preset", preset.id).apply()
    }

    fun selectDevice(device: RemoteDevice) {
        currentDevice = device
        SovereignLogger.i(SovereignLogger.Category.LIFECYCLE, "Device selected: [${device.deviceType}] ${device.name} (${device.ipAddress}:${device.port})")
        ArgusSessionHelper.persistSelectedDevice(prefs, device)
        startLivenessMonitoring()
    }

    fun toggleHideDevice(ip: String) {
        hiddenIps = if (hiddenIps.contains(ip)) hiddenIps - ip else hiddenIps + ip
        prefs.edit().putStringSet("hidden_device_ips", hiddenIps).apply()
        if (currentDevice?.ipAddress == ip) {
            currentDevice = ArgusSessionHelper.findNextEligibleDevice(discoveredDevices, hiddenIps, ignoredIps, currentNetworkId, currentSubnetPrefix)
            currentDevice?.let { prefs.edit().putString("last_selected_ip", it.ipAddress).apply() }
        }
    }

    fun deleteDevice(device: RemoteDevice) {
        discoveredDevices = discoveredDevices.filter { it.ipAddress != device.ipAddress }
        persistDevices(discoveredDevices)
        ignoredIps = ignoredIps + device.ipAddress
        prefs.edit().putStringSet("ignored_device_ips", ignoredIps).apply()
        if (hiddenIps.contains(device.ipAddress)) {
            hiddenIps = hiddenIps - device.ipAddress
            prefs.edit().putStringSet("hidden_device_ips", hiddenIps).apply()
        }
        if (currentDevice?.ipAddress == device.ipAddress) {
            val nextDevice = ArgusSessionHelper.findNextEligibleDevice(discoveredDevices, hiddenIps, ignoredIps, currentNetworkId, currentSubnetPrefix)
            currentDevice = nextDevice
            if (nextDevice != null) prefs.edit().putString("last_selected_ip", nextDevice.ipAddress).apply()
            else prefs.edit().remove("last_selected_ip").apply()
        }
        Toast.makeText(context, "Removed & forgotten ${device.name}", Toast.LENGTH_SHORT).show()
    }

    fun clearOtherNetworks() {
        val netId = currentNetworkId.takeIf { it.isNotBlank() } ?: return
        discoveredDevices = discoveredDevices.filter { it.effectiveNetworkId == netId }
        persistDevices(discoveredDevices)
        Toast.makeText(context, "Cleared other location devices", Toast.LENGTH_SHORT).show()
    }

    fun clearAllDevices() {
        discoveredDevices = emptyList(); currentDevice = null; ignoredIps = emptySet(); hiddenIps = emptySet()
        prefs.edit().remove("saved_devices_json").remove("last_selected_ip").remove("ignored_device_ips").remove("hidden_device_ips").apply()
        Toast.makeText(context, "All devices cleared", Toast.LENGTH_SHORT).show()
    }

    fun onResumeNetworkCheck() {
        NetworkEnvironmentManager.clearGatewayHardwareCache()
        registry.resetConnections()
        val fresh = NetworkEnvironmentManager.getActiveSubnetInfo(context)
        if (fresh?.networkId != activeSubnetInfo?.networkId) runDiscovery() else startLivenessMonitoring()
    }

    fun runDiscovery() {
        if (isScanning) return
        isScanning = true
        val freshNetInfo = NetworkEnvironmentManager.getActiveSubnetInfo(context)
        activeSubnetInfo = freshNetInfo
        val (activeNetId, activePrefix) = (freshNetInfo?.networkId ?: "") to (freshNetInfo?.subnetPrefix ?: "")
        SovereignLogger.i(SovereignLogger.Category.DISCOVERY, "runDiscovery() triggered (activeNetId=$activeNetId, subnet=$activePrefix)")

        val netLastIp = ArgusSessionHelper.resolveLastNetworkIp(prefs, activeNetId, activePrefix)
        if (activeNetId.isNotBlank()) {
            val netEligible = discoveredDevices.filter { !hiddenIps.contains(it.ipAddress) && SubnetInfo.isDeviceOnNetwork(it, activeNetId, activePrefix) }
            val restored = netEligible.find { it.ipAddress == netLastIp } ?: netEligible.firstOrNull()
            if (restored != null) currentDevice = restored
            else if (currentDevice != null && !SubnetInfo.isDeviceOnNetwork(currentDevice!!, activeNetId, activePrefix)) currentDevice = null
        }

        scope.launch {
            try {
                val gwIp = freshNetInfo?.gatewayIp
                if (!gwIp.isNullOrBlank() && freshNetInfo?.gatewayHardwareId == null) {
                    val probedHw = withContext(Dispatchers.IO) { NetworkEnvironmentManager.probeGatewayHardwareId(gwIp, forceFresh = true) }
                    if (probedHw != null) {
                        NetworkEnvironmentManager.recordGatewayHardwareId(context, gwIp, probedHw)
                        NetworkEnvironmentManager.getActiveSubnetInfo(context)?.let { activeSubnetInfo = it }
                    }
                }

                activeSessionIps = emptySet()
                val currentEffectiveNetId = activeSubnetInfo?.networkId ?: activeNetId
                val healed = ArgusSessionHelper.healLegacySubnetDevices(discoveredDevices, activePrefix, currentEffectiveNetId)
                if (healed != discoveredDevices) {
                    discoveredDevices = healed
                    persistDevices(healed)
                }

                val results = scanner.discoverAll(
                    timeoutMs = 3500,
                    onDeviceFound = { liveDev ->
                        if (ignoredIps.contains(liveDev.ipAddress)) return@discoverAll
                        activeSessionIps = activeSessionIps + liveDev.ipAddress
                        val targetNetId = activeSubnetInfo?.networkId ?: currentEffectiveNetId
                        val sanitized = ArgusSessionHelper.sanitizeDeviceForNetwork(liveDev, targetNetId)
                        discoveredDevices = DeviceSerializer.merge(discoveredDevices, listOf(sanitized))
                        persistDevices(discoveredDevices)
                        if (currentDevice == null && !hiddenIps.contains(sanitized.ipAddress) &&
                            (targetNetId.isBlank() || sanitized.effectiveNetworkId == targetNetId || (activePrefix.isNotBlank() && sanitized.effectiveSubnet == activePrefix))) {
                            currentDevice = sanitized
                            ArgusSessionHelper.persistSelectedDevice(prefs, sanitized)
                        }
                    }
                )
                val activeHwNetId = activeSubnetInfo?.networkId ?: currentEffectiveNetId
                val sanitizedResults = results.filterNot { ignoredIps.contains(it.ipAddress) }.map {
                    ArgusSessionHelper.sanitizeDeviceForNetwork(it, activeHwNetId)
                }
                activeSessionIps = activeSessionIps + sanitizedResults.map { it.ipAddress }
                discoveredDevices = DeviceSerializer.merge(discoveredDevices, sanitizedResults)
                persistDevices(discoveredDevices)

                val preferred = ArgusSessionHelper.selectPreferredDevice(
                    discoveredDevices, hiddenIps, ignoredIps, activeHwNetId, activePrefix, activeSessionIps, netLastIp
                )
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
            val unconfigured = ArgusSessionHelper.computeUnconfiguredIps(discoveredDevices, prefs)
            withContext(Dispatchers.Main) { unconfiguredIps = unconfigured }
        }
    }

    fun addManualIp(ip: String) {
        val cleanIp = ip.trim()
        if (!SovereignSanitizer.isValidIpv4(cleanIp)) { Toast.makeText(context, "Invalid IPv4 address format", Toast.LENGTH_SHORT).show(); return }
        scope.launch {
            val netInfo = NetworkEnvironmentManager.getActiveSubnetInfo(context)
            val subnet = NetworkEnvironmentManager.extractSubnetPrefix(cleanIp).takeIf { it.isNotBlank() }
            val probed = scanner.probeSpecificIp(cleanIp)
            val device = probed?.copy(subnetPrefix = subnet ?: probed.subnetPrefix, networkId = netInfo?.networkId ?: probed.networkId)
                ?: RemoteDevice("manual_$cleanIp", "Smart TV ($cleanIp)", cleanIp, 8008, DeviceType.GENERIC, subnetPrefix = subnet, networkId = netInfo?.networkId)
            discoveredDevices = DeviceSerializer.merge(discoveredDevices, listOf(device))
            persistDevices(discoveredDevices); selectDevice(device)
            Toast.makeText(context, "Added TV ($cleanIp)", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendKey(key: RemoteKey) {
        val initial = currentDevice ?: run {
            Toast.makeText(context, "Select or scan for a TV first", Toast.LENGTH_SHORT).show()
            return
        }
        var device = ArgusSessionHelper.autoHealAmazonDevice(initial)
        if (device != initial) {
            currentDevice = device
            discoveredDevices = DeviceSerializer.merge(discoveredDevices, listOf(device))
            persistDevices(discoveredDevices)
        }
        if (key == RemoteKey.POWER) {
            livenessState = DeviceLivenessState.WAKING
            val targetMac = device.macAddress ?: WakeOnLan.getMacFromArp(device.ipAddress)
            if (!targetMac.isNullOrBlank()) {
                if (device.macAddress == null) {
                    device = device.copy(macAddress = targetMac)
                    currentDevice = device
                    discoveredDevices = DeviceSerializer.merge(discoveredDevices, listOf(device))
                    persistDevices(discoveredDevices)
                }
                scope.launch(Dispatchers.IO) { WakeOnLan.wake(targetMac, device.ipAddress, context) }
            }
        }

        SovereignLogger.i(SovereignLogger.Category.PROTOCOL, "sendKey($key) -> ${device.name} (${device.ipAddress}:${device.port})")
        scope.launch {
            val dispatch = registry.sendKeyWithAutoPromotion(device, key)
            if (dispatch.isSuccess) {
                lastAckLatencyMs = dispatch.elapsedMs
                isAckActive = true
                SovereignLogger.i(SovereignLogger.Category.PROTOCOL, "sendKey($key) ACK received in ${dispatch.elapsedMs}ms from ${device.ipAddress}")
                if (key != RemoteKey.POWER) livenessState = DeviceLivenessState.ONLINE
                val promoted = dispatch.promotedDevice
                if (promoted != null && (promoted.deviceType != device.deviceType || promoted.port != device.port)) {
                    currentDevice = promoted
                    discoveredDevices = DeviceSerializer.merge(discoveredDevices, listOf(promoted))
                    persistDevices(discoveredDevices)
                    ArgusSessionHelper.persistSelectedDevice(prefs, promoted)
                    Toast.makeText(context, "Promoted to ${promoted.deviceType.displayName}!", Toast.LENGTH_SHORT).show()
                }
                scope.launch { delay(350); isAckActive = false }
            } else {
                lastAckLatencyMs = -1L
                isAckActive = true
                livenessState = DeviceLivenessState.DISCONNECTED
                val errMsg = dispatch.error?.message.orEmpty().lowercase()
                SovereignLogger.w(SovereignLogger.Category.PROTOCOL, "sendKey($key) FAILED on ${device.ipAddress}: ${dispatch.error?.message}")
                if (errMsg.contains("unauthorized") || errMsg.contains("permission") || errMsg.contains("denied")) {
                    Toast.makeText(context, "Pairing needed: Look at your TV screen and select 'Always Allow'", Toast.LENGTH_LONG).show()
                }
                scope.launch { delay(800); isAckActive = false }
            }
            if (key == RemoteKey.POWER) {
                scope.launch { ArgusSessionHelper.pollPowerWakeLiveness(device) { livenessState = it } }
            }
        }
    }

    private fun startLivenessMonitoring() {
        livenessJob?.cancel()
        val dev = currentDevice ?: run { livenessState = DeviceLivenessState.DISCONNECTED; return }
        livenessJob = scope.launch {
            while (isActive) {
                if (livenessState != DeviceLivenessState.WAKING) livenessState = DeviceLivenessTracker.probeLiveness(dev, 250)
                delay(4000)
            }
        }
    }
}
