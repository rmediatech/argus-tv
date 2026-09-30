package com.rmediatech.sovereignremote.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.protocols.TvProtocolRegistry
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.FileReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class UniversalTvScanner(
    private val context: Context,
    private val registry: TvProtocolRegistry
) {
    suspend fun discoverAll(timeoutMs: Int = 3500): List<RemoteDevice> = withContext(Dispatchers.IO) {
        val foundDevices = java.util.concurrent.ConcurrentHashMap<String, RemoteDevice>()
        val protocols = registry.getProtocols()

        // 1. Gather candidate local IPs from ARP cache and active subnet ranges
        val candidateIps = mutableSetOf<String>()
        candidateIps.addAll(readArpTable())

        val baseSubnet = getLocalSubnetPrefix()
        for (i in 150..195) candidateIps.add("$baseSubnet.$i")
        for (i in 2..30) candidateIps.add("$baseSubnet.$i")
        for (i in 100..125) candidateIps.add("$baseSubnet.$i")

        // 2. Parallel Polymorphic Probing: Zero-overhead TCP pre-flight check before protocol probe
        val probeJobs = candidateIps.map { ip ->
            async {
                for (protocol in protocols) {
                    if (isPortReachable(ip, protocol.defaultPort, 150)) {
                        val device = protocol.probe(ip, protocol.defaultPort)
                        if (device != null) {
                            foundDevices[ip] = device
                            break
                        }
                    }
                }
            }
        }

        // 3. Roku SSDP Multicast Scanner on Port 1900
        val ssdpJob = async {
            discoverRokuSsdp(timeoutMs = 2000, foundDevices)
        }

        withTimeoutOrNull(timeoutMs.toLong()) {
            probeJobs.awaitAll()
            ssdpJob.await()
        }

        foundDevices.values.toList().sortedBy { it.name }
    }

    suspend fun probeSpecificIp(ip: String): RemoteDevice? = withContext(Dispatchers.IO) {
        for (protocol in registry.getProtocols()) {
            if (isPortReachable(ip, protocol.defaultPort, 300)) {
                val device = protocol.probe(ip, protocol.defaultPort)
                if (device != null) return@withContext device
            }
        }

        // Generic fallback
        RemoteDevice(
            id = "generic_$ip",
            name = "Smart TV ($ip)",
            ipAddress = ip,
            port = 8008,
            deviceType = com.rmediatech.sovereignremote.model.DeviceType.GENERIC
        )
    }

    private fun isPortReachable(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun discoverRokuSsdp(timeoutMs: Int, foundDevices: MutableMap<String, RemoteDevice>) {
        var multicastLock: WifiManager.MulticastLock? = null
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("universal_ssdp_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }

            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val mSearch = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nST: roku:ecp\r\nMX: 2\r\n\r\n".toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(mSearch, mSearch.size, InetAddress.getByName("239.255.255.250"), 1900)
                socket.send(packet)
                socket.send(packet)

                val buffer = ByteArray(2048)
                val responsePacket = DatagramPacket(buffer, buffer.size)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    try {
                        socket.receive(responsePacket)
                        val ip = responsePacket.address.hostAddress ?: continue
                        if (!foundDevices.containsKey(ip)) {
                            val responseStr = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8)
                            if (responseStr.contains("roku:ecp", ignoreCase = true)) {
                                runBlocking {
                                    val roku = registry.get(com.rmediatech.sovereignremote.model.DeviceType.ROKU).probe(ip, 8060)
                                    if (roku != null) {
                                        synchronized(foundDevices) {
                                            foundDevices[ip] = roku
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            try { multicastLock?.release() } catch (_: Exception) {}
        }
    }

    private fun readArpTable(): Set<String> {
        val ips = mutableSetOf<String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                var line = reader.readLine() // Skip header
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line?.split(Regex("\\s+")) ?: continue
                    if (tokens.size >= 4 && tokens[3] != "00:00:00:00:00:00") {
                        val ip = tokens[0]
                        if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                            ips.add(ip)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return ips
    }

    private fun getLocalSubnetPrefix(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        val parts = hostAddress.split(".")
                        if (parts.size == 4) {
                            return "${parts[0]}.${parts[1]}.${parts[2]}"
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifi?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                String.format(
                    "%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff
                )
            } else "192.168.1"
        } catch (_: Exception) {
            "192.168.1"
        }
    }
}
