package com.rmediatech.sovereignremote.net

import android.content.Context
import com.rmediatech.sovereignremote.model.RemoteDevice
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
    val gatewayHardwareId: String? = null,
    val networkId: String = computeNetworkId(gatewayMac, subnetPrefix, gatewayHardwareId)
) {
    fun isDeviceOnThisNetwork(device: RemoteDevice): Boolean {
        // 1. If both active network and device have hardware router fingerprint, strict match required!
        if (networkId.startsWith("net_hw_") && device.effectiveNetworkId.startsWith("net_hw_")) {
            return device.effectiveNetworkId == networkId
        }
        // 2. Exact match
        if (device.effectiveNetworkId == networkId) return true
        // 3. Fallback only if device is an un-fingerprinted legacy subnet record
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

object NetworkEnvironmentManager {
    private const val TAG = "NetworkEnv"
    private val cachedGatewayHwMap = ConcurrentHashMap<String, String>()

    fun getCachedGatewayHardwareId(context: Context?, gatewayIp: String?): String? {
        if (gatewayIp.isNullOrBlank()) return null
        cachedGatewayHwMap[gatewayIp]?.let { return it }
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences("sovereign_network_env", Context.MODE_PRIVATE)
                val stored = prefs.getString("gw_hw_$gatewayIp", null)
                if (!stored.isNullOrBlank()) {
                    cachedGatewayHwMap[gatewayIp] = stored
                    return stored
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun recordGatewayHardwareId(context: Context?, gatewayIp: String?, hardwareId: String) {
        val clean = hardwareId.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase().take(16)
        if (clean.isNotBlank() && !gatewayIp.isNullOrBlank()) {
            cachedGatewayHwMap[gatewayIp] = clean
            context?.let { ctx ->
                try {
                    ctx.getSharedPreferences("sovereign_network_env", Context.MODE_PRIVATE)
                        .edit()
                        .putString("gw_hw_$gatewayIp", clean)
                        .apply()
                } catch (_: Exception) {}
            }
            Log.d(TAG, "Recorded gateway hardware fingerprint for $gatewayIp: $clean")
        }
    }

    fun clearGatewayHardwareCache() {
        cachedGatewayHwMap.clear()
    }

    fun probeGatewayHardwareId(gatewayIp: String, forceFresh: Boolean = false): String? {
        if (gatewayIp.isBlank()) return null
        if (!forceFresh) {
            cachedGatewayHwMap[gatewayIp]?.let { return it }
        }

        // 1. Fast TLS Cert Fingerprint on Port 443 (150ms timeout)
        try {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAll, SecureRandom())
            val factory = sslContext.socketFactory
            (factory.createSocket() as SSLSocket).use { s ->
                s.soTimeout = 150
                s.connect(InetSocketAddress(gatewayIp, 443), 150)
                s.startHandshake()
                val cert = s.session.peerCertificates.firstOrNull() as? X509Certificate
                if (cert != null) {
                    val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                    val fp = digest.take(8).joinToString("") { "%02x".format(it) }
                    cachedGatewayHwMap[gatewayIp] = fp
                    Log.d(TAG, "Probed gateway TLS cert fingerprint: $fp on $gatewayIp:443")
                    return fp
                }
            }
        } catch (_: Throwable) {}

        // 2. Fast HTTP Banner / Realm on Port 80 (150ms timeout)
        try {
            Socket().use { s ->
                s.soTimeout = 150
                s.connect(InetSocketAddress(gatewayIp, 80), 150)
                val out = s.getOutputStream()
                out.write("HEAD / HTTP/1.0\r\nHost: $gatewayIp\r\n\r\n".toByteArray(Charsets.US_ASCII))
                out.flush()
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.US_ASCII))
                val sb = StringBuilder()
                var line: String?
                var linesRead = 0
                while (reader.readLine().also { line = it } != null && linesRead++ < 15) {
                    val l = line?.lowercase() ?: continue
                    if (l.startsWith("server:") || l.startsWith("www-authenticate:") || l.startsWith("location:")) {
                        sb.append(line).append(";")
                    }
                }
                if (sb.isNotEmpty()) {
                    val digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().toByteArray(Charsets.UTF_8))
                    val fp = digest.take(8).joinToString("") { "%02x".format(it) }
                    cachedGatewayHwMap[gatewayIp] = fp
                    Log.d(TAG, "Probed gateway HTTP banner fingerprint: $fp on $gatewayIp:80")
                    return fp
                }
            }
        } catch (_: Throwable) {}

        return null
    }

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
                                    val gwHwId = getCachedGatewayHardwareId(context, gwIp)
                                    val info = computeSubnetInfo(addr, la.prefixLength, gwIp, gwMac, gwHwId)
                                    if (info != null) {
                                        Log.d(TAG, "Resolved active subnet via ConnectivityManager: $info (networkId=${info.networkId})")
                                        com.rmediatech.sovereignremote.logging.SovereignLogger.i(
                                            com.rmediatech.sovereignremote.logging.SovereignLogger.Category.NETWORK,
                                            "Active subnet resolved: ${info.localIp}/${info.prefixLength} (gw=${info.gatewayIp}, gwMac=${info.gatewayMac}, hwId=${info.gatewayHardwareId}, netId=${info.networkId})"
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
                val gwHwId = getCachedGatewayHardwareId(context, gwIp)

                for (intf in interfaces) {
                    if (intf.isLoopback) continue
                    val name = intf.name.lowercase()
                    val isLan = name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en") || name.startsWith("tiwlan")
                    if (!isLan) continue

                    for (ia in intf.interfaceAddresses) {
                        val addr = ia.address
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val prefix = ia.networkPrefixLength.toInt()
                            val info = computeSubnetInfo(addr, if (prefix in 8..30) prefix else 24, gwIp, gwMac, gwHwId)
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
                    val gwHwId = getCachedGatewayHardwareId(context, gwIp)
                    return SubnetInfo(
                        localIp = ipStr,
                        subnetPrefix = prefix,
                        cidr = "$prefix.0/24",
                        prefixLength = 24,
                        broadcastIp = "$prefix.255",
                        gatewayIp = gwIp,
                        gatewayMac = gwMac,
                        gatewayHardwareId = gwHwId,
                        networkId = SubnetInfo.computeNetworkId(gwMac, prefix, gwHwId)
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
        gatewayMac: String? = null,
        gatewayHardwareId: String? = null
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
            gatewayHardwareId = gatewayHardwareId,
            networkId = SubnetInfo.computeNetworkId(gatewayMac, subnetPrefix, gatewayHardwareId)
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
