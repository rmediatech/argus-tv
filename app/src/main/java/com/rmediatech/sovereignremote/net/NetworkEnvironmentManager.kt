package com.rmediatech.sovereignremote.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Sovereign Network Environment Manager:
 * Identifies the active LAN/Wi-Fi network context, computes precise subnet CIDR boundaries,
 * and eliminates cross-network contamination (e.g. showing home devices when on a friend's Wi-Fi).
 */
data class SubnetInfo(
    val localIp: String,
    val subnetPrefix: String, // e.g. "192.168.0", "10.0.0"
    val cidr: String,         // e.g. "192.168.0.0/24"
    val prefixLength: Int,    // e.g. 24
    val broadcastIp: String,  // e.g. "192.168.0.255"
    val gatewayIp: String? = null,
    val gatewayMac: String? = null,
    val networkId: String = computeNetworkId(gatewayMac, subnetPrefix)
) {
    companion object {
        fun computeNetworkId(gatewayMac: String?, subnetPrefix: String): String {
            val cleanMac = gatewayMac?.replace("[:-]".toRegex(), "")?.lowercase()?.trim()
            return if (!cleanMac.isNullOrBlank() && cleanMac != "000000000000") {
                "net_${cleanMac}_${subnetPrefix}"
            } else {
                "net_subnet_${subnetPrefix}"
            }
        }
    }
}

object NetworkEnvironmentManager {
    private const val TAG = "NetworkEnv"

    /**
     * Resolves the active network's IPv4 configuration.
     * Prioritizes active Wi-Fi and Ethernet interfaces via modern ConnectivityManager (checking all active networks).
     * Falls back to network interface enumeration and /proc/net/route gateway discovery.
     */
    fun getActiveSubnetInfo(context: Context): SubnetInfo? {
        var gwIp: String? = null

        // 1. Modern ConnectivityManager: Inspect active network and allNetworks
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val candidateNetworks = mutableListOf<android.net.Network>()
                cm.activeNetwork?.let { candidateNetworks.add(it) }
                for (net in cm.allNetworks) {
                    if (!candidateNetworks.contains(net)) candidateNetworks.add(net)
                }

                for (net in candidateNetworks) {
                    val caps = cm.getNetworkCapabilities(net) ?: continue
                    val isLan = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

                    if (isLan) {
                        val linkProps = cm.getLinkProperties(net)
                        if (linkProps != null) {
                            for (route in linkProps.routes) {
                                if (route.isDefaultRoute && route.gateway is Inet4Address) {
                                    gwIp = route.gateway?.hostAddress
                                    break
                                }
                            }

                            for (la in linkProps.linkAddresses) {
                                val addr = la.address
                                if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                                    if (gwIp.isNullOrBlank()) gwIp = getDefaultGatewayIpFromRoute()
                                    val gwMac = gwIp?.let { WakeOnLan.getMacFromArp(it) }
                                    val info = computeSubnetInfo(addr, la.prefixLength, gwIp, gwMac)
                                    if (info != null) {
                                        Log.d(TAG, "Resolved active subnet via ConnectivityManager: $info (networkId=${info.networkId})")
                                        com.rmediatech.sovereignremote.logging.SovereignLogger.i(
                                            com.rmediatech.sovereignremote.logging.SovereignLogger.Category.NETWORK,
                                            "Active subnet resolved: ${info.localIp}/${info.prefixLength} (gw=${info.gatewayIp}, gwMac=${info.gatewayMac}, netId=${info.networkId})"
                                        )
                                        return info
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ConnectivityManager resolution failed: ${e.message}")
        }

        // 2. Fallback: NetworkInterface enumeration targeting LAN interfaces (wlan, eth)
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                if (gwIp.isNullOrBlank()) gwIp = getDefaultGatewayIpFromRoute()
                val gwMac = gwIp?.let { WakeOnLan.getMacFromArp(it) }

                for (intf in interfaces) {
                    if (intf.isLoopback) continue
                    val name = intf.name.lowercase()
                    val isLan = name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en") || name.startsWith("tiwlan")
                    if (!isLan) continue

                    for (ia in intf.interfaceAddresses) {
                        val addr = ia.address
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val prefix = ia.networkPrefixLength.toInt()
                            val info = computeSubnetInfo(addr, if (prefix in 8..30) prefix else 24, gwIp, gwMac)
                            if (info != null) {
                                Log.d(TAG, "Resolved active subnet via NetworkInterface (${intf.name}): $info (networkId=${info.networkId})")
                                return info
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "NetworkInterface resolution failed: ${e.message}")
        }

        // 3. Fallback: WifiManager DHCP Info
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp = wifi?.dhcpInfo
            if (dhcp != null && dhcp.ipAddress != 0) {
                val ipInt = dhcp.ipAddress
                val ipStr = String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
                val parts = ipStr.split(".")
                if (parts.size == 4) {
                    val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                    if (gwIp.isNullOrBlank()) gwIp = getDefaultGatewayIpFromRoute()
                    val gwMac = gwIp?.let { WakeOnLan.getMacFromArp(it) }
                    return SubnetInfo(
                        localIp = ipStr,
                        subnetPrefix = prefix,
                        cidr = "$prefix.0/24",
                        prefixLength = 24,
                        broadcastIp = "$prefix.255",
                        gatewayIp = gwIp,
                        gatewayMac = gwMac,
                        networkId = SubnetInfo.computeNetworkId(gwMac, prefix)
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiManager resolution failed: ${e.message}")
        }

        return null
    }

    /**
     * Resolves the default gateway IPv4 address from kernel routing tables (/proc/net/route).
     */
    fun getDefaultGatewayIpFromRoute(): String? {
        try {
            java.io.BufferedReader(java.io.FileReader("/proc/net/route")).use { reader ->
                var line = reader.readLine() // Header
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line?.split(Regex("\\s+")) ?: continue
                    if (tokens.size >= 8) {
                        val dest = tokens[1]
                        val flags = tokens[3].toIntOrNull(16) ?: 0
                        // Default route has destination 00000000 and RTF_GATEWAY (0x2) flag set
                        if (dest == "00000000" && (flags and 0x2 != 0)) {
                            val gwHex = tokens[2]
                            if (gwHex.length == 8) {
                                val ip0 = gwHex.substring(6, 8).toInt(16)
                                val ip1 = gwHex.substring(4, 6).toInt(16)
                                val ip2 = gwHex.substring(2, 4).toInt(16)
                                val ip3 = gwHex.substring(0, 2).toInt(16)
                                return "$ip0.$ip1.$ip2.$ip3"
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Computes the SubnetInfo from an Inet4Address and CIDR prefix length.
     */
    fun computeSubnetInfo(
        addr: Inet4Address,
        prefixLength: Int,
        gatewayIp: String? = null,
        gatewayMac: String? = null
    ): SubnetInfo? {
        val hostIp = addr.hostAddress ?: return null
        val parts = hostIp.split(".")
        if (parts.size != 4) return null

        val safePrefix = if (prefixLength in 8..30) prefixLength else 24
        val bytes = addr.address
        val ipInt = ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)

        val mask = if (safePrefix == 0) 0 else (-1 shl (32 - safePrefix))
        val netInt = ipInt and mask
        val bcastInt = ipInt or mask.inv()

        val subnetPrefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        val baseIp = intToIp(netInt)
        val broadcastIp = intToIp(bcastInt)

        return SubnetInfo(
            localIp = hostIp,
            subnetPrefix = subnetPrefix,
            cidr = "$baseIp/$safePrefix",
            prefixLength = safePrefix,
            broadcastIp = broadcastIp,
            gatewayIp = gatewayIp,
            gatewayMac = gatewayMac,
            networkId = SubnetInfo.computeNetworkId(gatewayMac, subnetPrefix)
        )
    }

    /**
     * Generates candidate host IPs to probe on the active network.
     * Bounds the sweep to a maximum of 254 hosts to prevent allocation exhaustion.
     */
    fun generateCandidateIps(info: SubnetInfo?): List<String> {
        val candidateIps = mutableListOf<String>()
        if (info == null) {
            // Check top 3 standard consumer subnets as fallback: Comcast/Xfinity, Netgear/TP-Link, Linksys/Asus
            for (fallback in listOf("192.168.1", "192.168.0", "10.0.0")) {
                for (i in 1..254) {
                    candidateIps.add("$fallback.$i")
                }
            }
            return candidateIps
        }

        val prefix = info.subnetPrefix
        for (i in 1..254) {
            candidateIps.add("$prefix.$i")
        }
        return candidateIps
    }

    fun extractSubnetPrefix(ip: String): String {
        val lastDot = ip.lastIndexOf('.')
        return if (lastDot > 0) ip.substring(0, lastDot) else ""
    }

    fun matchesSubnet(ip: String, subnetPrefix: String): Boolean {
        if (subnetPrefix.isBlank()) return true
        return ip.startsWith("$subnetPrefix.")
    }

    private fun intToIp(value: Int): String {
        return "${(value ushr 24) and 0xFF}.${(value ushr 16) and 0xFF}.${(value ushr 8) and 0xFF}.${value and 0xFF}"
    }
}
