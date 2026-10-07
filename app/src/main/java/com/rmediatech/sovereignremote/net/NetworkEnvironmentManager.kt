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
    val broadcastIp: String   // e.g. "192.168.0.255"
)

object NetworkEnvironmentManager {
    private const val TAG = "NetworkEnv"

    /**
     * Resolves the active network's IPv4 configuration.
     * Prioritizes active Wi-Fi and Ethernet interfaces via modern ConnectivityManager.
     * Falls back to network interface interrogation and Wi-Fi DHCP info.
     */
    fun getActiveSubnetInfo(context: Context): SubnetInfo? {
        // 1. Modern ConnectivityManager (Android 10+ standard)
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            if (activeNetwork != null) {
                val caps = cm.getNetworkCapabilities(activeNetwork)
                val isLan = caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                )

                if (isLan) {
                    val linkProps = cm.getLinkProperties(activeNetwork)
                    if (linkProps != null) {
                        for (la in linkProps.linkAddresses) {
                            val addr = la.address
                            if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                                val info = computeSubnetInfo(addr, la.prefixLength)
                                if (info != null) {
                                    Log.d(TAG, "Resolved active subnet via ConnectivityManager: $info")
                                    return info
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
                for (intf in interfaces) {
                    if (intf.isLoopback || !intf.isUp) continue
                    val name = intf.name.lowercase()
                    val isLan = name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en") || name.startsWith("tiwlan")
                    if (!isLan) continue

                    for (ia in intf.interfaceAddresses) {
                        val addr = ia.address
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val prefix = ia.networkPrefixLength.toInt()
                            val info = computeSubnetInfo(addr, if (prefix in 8..30) prefix else 24)
                            if (info != null) {
                                Log.d(TAG, "Resolved active subnet via NetworkInterface (${intf.name}): $info")
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
                    return SubnetInfo(
                        localIp = ipStr,
                        subnetPrefix = prefix,
                        cidr = "$prefix.0/24",
                        prefixLength = 24,
                        broadcastIp = "$prefix.255"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiManager resolution failed: ${e.message}")
        }

        return null
    }

    /**
     * Computes the SubnetInfo from an Inet4Address and CIDR prefix length.
     */
    fun computeSubnetInfo(addr: Inet4Address, prefixLength: Int): SubnetInfo? {
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
            broadcastIp = broadcastIp
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
