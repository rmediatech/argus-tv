package com.rmediatech.sovereignremote.state

import android.content.SharedPreferences
import com.rmediatech.sovereignremote.discovery.SmartTvClassifier
import com.rmediatech.sovereignremote.model.DeviceLivenessState
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.net.DeviceLivenessTracker
import com.rmediatech.sovereignremote.net.SubnetInfo
import com.rmediatech.sovereignremote.security.SovereignSanitizer
import kotlinx.coroutines.delay

/**
 * Sovereign Argus Session Domain Helper:
 * One Job: Pure domain helpers for stale-device filtering, preferred online device selection,
 * Fire TV protocol healing, unconfigured device checks, and two-phase power-wake liveness polling.
 */
object ArgusSessionHelper {

    fun cleanStaleDevices(
        devices: List<RemoteDevice>,
        ignoredIps: Set<String>
    ): List<RemoteDevice> {
        return devices.filterNot {
            (it.deviceType == DeviceType.GENERIC && (it.port == 8080 || it.port == 80 || it.port == 60000)) ||
                (it.deviceType == DeviceType.SONY_BRAVIA && it.ipAddress.endsWith(".1") && it.port == 80) ||
                ignoredIps.contains(it.ipAddress)
        }
    }

    fun resolveLastNetworkIp(prefs: SharedPreferences, netId: String, subnetPrefix: String): String? {
        if (netId.isBlank()) return prefs.getString("last_selected_ip", null)
        return prefs.getString("last_selected_ip_for_$netId", null)
            ?: (if (subnetPrefix.isNotBlank()) prefs.getString("last_selected_ip_for_$subnetPrefix", null) else null)
            ?: prefs.getString("last_selected_ip", null)
    }

    fun persistSelectedDevice(prefs: SharedPreferences, device: RemoteDevice) {
        prefs.edit()
            .putString("last_selected_ip", device.ipAddress)
            .putString("last_selected_ip_for_${device.effectiveNetworkId}", device.ipAddress)
            .putString("last_selected_ip_for_${device.effectiveSubnet}", device.ipAddress)
            .apply()
    }

    fun healLegacySubnetDevices(
        devices: List<RemoteDevice>,
        activePrefix: String,
        currentEffectiveNetId: String
    ): List<RemoteDevice> {
        if (activePrefix.isBlank() || !currentEffectiveNetId.startsWith("net_hw_")) return devices
        return devices.map { dev ->
            if (dev.networkId?.startsWith("net_subnet_") == true && dev.effectiveSubnet == activePrefix) {
                dev.copy(networkId = currentEffectiveNetId)
            } else dev
        }
    }

    fun sanitizeDeviceForNetwork(device: RemoteDevice, targetNetId: String): RemoteDevice {
        return device.copy(
            name = SovereignSanitizer.sanitizeDeviceName(device.name),
            modelName = SovereignSanitizer.sanitizeModelName(device.modelName),
            networkId = targetNetId.takeIf { it.isNotBlank() } ?: device.networkId
        )
    }

    fun findNextEligibleDevice(
        devices: List<RemoteDevice>,
        hiddenIps: Set<String>,
        ignoredIps: Set<String>,
        netId: String,
        subnetPrefix: String
    ): RemoteDevice? {
        return devices.find {
            !hiddenIps.contains(it.ipAddress) && !ignoredIps.contains(it.ipAddress) &&
                (netId.isBlank() || it.effectiveNetworkId == netId || (subnetPrefix.isNotBlank() && it.effectiveSubnet == subnetPrefix))
        }
    }

    fun selectPreferredDevice(
        merged: List<RemoteDevice>,
        hiddenIps: Set<String>,
        ignoredIps: Set<String>,
        activeHwNetId: String,
        activePrefix: String,
        activeSessionIps: Set<String>,
        prefIp: String?
    ): RemoteDevice? {
        val eligible = merged.filter {
            !hiddenIps.contains(it.ipAddress) && !ignoredIps.contains(it.ipAddress) &&
                SubnetInfo.isDeviceOnNetwork(it, activeHwNetId, activePrefix)
        }
        val onlineConfirmed = eligible.filter { activeSessionIps.contains(it.ipAddress) }
        val candidates = if (onlineConfirmed.isNotEmpty()) onlineConfirmed else eligible
        return candidates.find { it.ipAddress == prefIp }
            ?: candidates.find {
                it.deviceType == DeviceType.FIRE_TV ||
                    it.deviceType == DeviceType.GOOGLE_TV ||
                    it.deviceType == DeviceType.ROKU
            }
            ?: candidates.firstOrNull()
    }

    fun autoHealAmazonDevice(device: RemoteDevice): RemoteDevice {
        val isAmazon = device.name.contains("Fire", ignoreCase = true) ||
            device.modelName?.contains("Fire", ignoreCase = true) == true ||
            SmartTvClassifier.inferBrandFromMac(device.macAddress) == DeviceType.FIRE_TV
        return if (isAmazon && (device.deviceType != DeviceType.FIRE_TV || device.port != 5555)) {
            device.copy(
                deviceType = DeviceType.FIRE_TV,
                port = 5555,
                modelName = if (device.modelName.isNullOrBlank() || device.modelName == "Google TV") {
                    "Amazon Fire TV"
                } else device.modelName
            )
        } else device
    }

    fun computeUnconfiguredIps(
        devices: List<RemoteDevice>,
        prefs: SharedPreferences
    ): Set<String> {
        val unconfigured = mutableSetOf<String>()
        for (dev in devices) {
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
        return unconfigured
    }

    suspend fun pollPowerWakeLiveness(
        device: RemoteDevice,
        onStateResolved: (DeviceLivenessState) -> Unit
    ) {
        for (i in 1..12) {
            delay(120)
            if (DeviceLivenessTracker.probeLiveness(device, 100) == DeviceLivenessState.ONLINE) {
                onStateResolved(DeviceLivenessState.ONLINE)
                return
            }
        }
        for (i in 1..8) {
            delay(350)
            if (DeviceLivenessTracker.probeLiveness(device, 200) == DeviceLivenessState.ONLINE) {
                onStateResolved(DeviceLivenessState.ONLINE)
                return
            }
        }
        onStateResolved(DeviceLivenessTracker.probeLiveness(device, 250))
    }
}
