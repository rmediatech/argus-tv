package com.rmediatech.sovereignremote.net

import com.rmediatech.sovereignremote.model.RemoteDevice

/**
 * Sovereign Subnet & Hardware Router Identity Context (`SOVEREIGN-IDENTITY-ANCHOR-01`):
 * One Job: Encapsulates active LAN subnet CIDR boundaries and hardware router networkId matching.
 */
data class SubnetInfo(
    val localIp: String,
    val subnetPrefix: String, // e.g. "192.168.0", "10.0.0"
    val cidr: String,         // e.g. "192.168.0.0/24"
    val prefixLength: Int,    // e.g. 24
    val broadcastIp: String,  // e.g. "192.168.0.255"
    val gatewayIp: String? = null,
    val gatewayMac: String? = null,
    val gatewayHardwareId: String? = null,
    val networkId: String = computeNetworkId(gatewayMac, subnetPrefix, gatewayHardwareId)
) {
    fun isDeviceOnThisNetwork(device: RemoteDevice): Boolean {
        if (networkId.startsWith("net_hw_") && device.effectiveNetworkId.startsWith("net_hw_")) {
            return device.effectiveNetworkId == networkId
        }
        if (device.effectiveNetworkId == networkId) return true
        if (device.networkId?.startsWith("net_subnet_") == true && device.effectiveSubnet == subnetPrefix) {
            return true
        }
        return false
    }

    companion object {
        fun computeNetworkId(gatewayMac: String?, subnetPrefix: String, gatewayHardwareId: String? = null): String {
            val cleanMac = gatewayMac?.replace("[:-]".toRegex(), "")?.lowercase()?.trim()
            if (!cleanMac.isNullOrBlank() && cleanMac != "000000000000") {
                return "net_${cleanMac}_${subnetPrefix}"
            }
            val cleanHw = gatewayHardwareId?.replace("[^a-zA-Z0-9]".toRegex(), "")?.lowercase()?.take(16)
            if (!cleanHw.isNullOrBlank()) {
                return "net_hw_${cleanHw}_${subnetPrefix}"
            }
            return "net_subnet_${subnetPrefix}"
        }

        fun isDeviceOnNetwork(device: RemoteDevice, activeNetworkId: String?, activeSubnetPrefix: String?): Boolean {
            if (activeNetworkId.isNullOrBlank()) {
                return !activeSubnetPrefix.isNullOrBlank() && device.effectiveSubnet == activeSubnetPrefix
            }
            if (activeNetworkId.startsWith("net_hw_") && device.effectiveNetworkId.startsWith("net_hw_")) {
                return device.effectiveNetworkId == activeNetworkId
            }
            if (device.effectiveNetworkId == activeNetworkId) return true
            if (device.networkId?.startsWith("net_subnet_") == true && !activeSubnetPrefix.isNullOrBlank() && device.effectiveSubnet == activeSubnetPrefix) {
                return true
            }
            return false
        }
    }
}
