package com.rmediatech.sovereignremote.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.net.NetworkEnvironmentManager
import com.rmediatech.sovereignremote.protocols.TvProtocolRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal Sovereign Smart TV & Media Device Scanner:
 * Polymorphic multi-protocol discovery utilizing targeted SSDP multicast, directed subnet broadcast,
 * and high-throughput concurrent TCP port sweeps across active LAN subnets.
 */
class UniversalTvScanner(
    private val context: Context,
    private val registry: TvProtocolRegistry
) {
    companion object {
        private const val TAG = "UniversalTvScanner"
    }

    suspend fun discoverAll(
        timeoutMs: Int = 4500,
        onDeviceFound: ((RemoteDevice) -> Unit)? = null
    ): List<RemoteDevice> = withContext(Dispatchers.IO) {
        val foundDevices = ConcurrentHashMap<String, RemoteDevice>()
        val arpMappings = com.rmediatech.sovereignremote.net.WakeOnLan.getAllArpMappings()

        // 1. Resolve active network subnet environment
        val activeSubnetInfo = NetworkEnvironmentManager.getActiveSubnetInfo(context)
        val candidateIps = NetworkEnvironmentManager.generateCandidateIps(activeSubnetInfo).toMutableSet()
        candidateIps.addAll(arpMappings.keys)

        fun recordDevice(device: RemoteDevice) {
            val existing = foundDevices[device.ipAddress]
            if (existing == null || existing.deviceType == DeviceType.GENERIC) {
                val enriched = if (device.macAddress.isNullOrBlank()) {
                    device.copy(macAddress = arpMappings[device.ipAddress] ?: com.rmediatech.sovereignremote.net.WakeOnLan.getMacFromArp(device.ipAddress))
                } else device
                foundDevices[device.ipAddress] = enriched
                onDeviceFound?.invoke(enriched)
            }
        }

        // 2. High-speed Multi-Target SSDP Multicast & Subnet Broadcast
        val ssdpJob = launch {
            discoverSsdp(
                timeoutMs = (timeoutMs * 0.85).toInt(),
                broadcastIp = activeSubnetInfo?.broadcastIp,
                onDeviceDiscovered = { dev -> recordDevice(dev) }
            )
        }

        // 3. High-throughput bounded TCP sweep (Semaphore bounds concurrency to avoid thread starvation)
        val concurrencyLimit = Semaphore(50)
        val probeJobs = candidateIps.map { ip ->
            launch {
                concurrencyLimit.withPermit {
                    // Check primary smart TV ports: Roku (8060), Google TV (8008), Fire TV (5555), Samsung (8001)
                    val protocols = registry.getProtocols()
                    for (protocol in protocols) {
                        if (foundDevices.containsKey(ip)) break
                        if (isPortReachable(ip, protocol.defaultPort, 140)) {
                            val device = protocol.probe(ip, protocol.defaultPort)
                            if (device != null) {
                                recordDevice(device)
                                break
                            }
                        }
                    }
                }
            }
        }

        withTimeoutOrNull(timeoutMs.toLong()) {
            probeJobs.joinAll()
            ssdpJob.join()
        }

        val latestArp = com.rmediatech.sovereignremote.net.WakeOnLan.getAllArpMappings()
        foundDevices.values.map { dev ->
            if (dev.macAddress.isNullOrBlank()) {
                dev.copy(macAddress = latestArp[dev.ipAddress])
            } else {
                dev
            }
        }.sortedBy { it.name }
    }

    suspend fun probeSpecificIp(ip: String): RemoteDevice? = withContext(Dispatchers.IO) {
        val mac = com.rmediatech.sovereignremote.net.WakeOnLan.getMacFromArp(ip)
        for (protocol in registry.getProtocols()) {
            if (isPortReachable(ip, protocol.defaultPort, 300)) {
                val device = protocol.probe(ip, protocol.defaultPort)
                if (device != null) {
                    return@withContext if (device.macAddress.isNullOrBlank()) device.copy(macAddress = mac) else device
                }
            }
        }

        // Generic fallback
        RemoteDevice(
            id = "generic_$ip",
            name = "Smart TV ($ip)",
            ipAddress = ip,
            port = 8008,
            deviceType = DeviceType.GENERIC,
            macAddress = mac
        )
    }

    private fun isPortReachable(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun discoverSsdp(
        timeoutMs: Int,
        broadcastIp: String?,
        onDeviceDiscovered: (RemoteDevice) -> Unit
    ) = withContext(Dispatchers.IO) {
        var multicastLock: WifiManager.MulticastLock? = null
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("universal_tv_ssdp_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }

            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 800

                val targets = mutableListOf<InetAddress>()
                try { targets.add(InetAddress.getByName("239.255.255.250")) } catch (_: Exception) {}
                if (!broadcastIp.isNullOrBlank()) {
                    try { targets.add(InetAddress.getByName(broadcastIp)) } catch (_: Exception) {}
                }

                val searchQueries = listOf(
                    "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nST: roku:ecp\r\nMX: 2\r\n\r\n",
                    "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nST: urn:dial-multiscreen-org:service:dial:1\r\nMX: 2\r\n\r\n",
                    "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nST: ssdp:all\r\nMX: 2\r\n\r\n",
                    "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\nMX: 2\r\n\r\n"
                )

                // Transmit SSDP search requests
                for (query in searchQueries) {
                    val bytes = query.toByteArray(Charsets.UTF_8)
                    for (target in targets) {
                        try {
                            val packet = DatagramPacket(bytes, bytes.size, target, 1900)
                            socket.send(packet)
                        } catch (e: Exception) {
                            Log.w(TAG, "SSDP transmission failed to $target: ${e.message}")
                        }
                    }
                }

                val buffer = ByteArray(2048)
                val responsePacket = DatagramPacket(buffer, buffer.size)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeoutMs && isActive) {
                    try {
                        socket.receive(responsePacket)
                        val ip = responsePacket.address.hostAddress ?: continue
                        val responseStr = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8)

                        if (responseStr.contains("roku:ecp", ignoreCase = true) || responseStr.contains(":8060", ignoreCase = true)) {
                            val roku = registry.get(DeviceType.ROKU).probe(ip, 8060)
                            if (roku != null) onDeviceDiscovered(roku)
                        } else if (responseStr.contains("dial", ignoreCase = true) || responseStr.contains(":8008", ignoreCase = true)) {
                            val googleTv = registry.get(DeviceType.GOOGLE_TV).probe(ip, 8008)
                            if (googleTv != null) onDeviceDiscovered(googleTv)
                        } else if (responseStr.contains("Samsung", ignoreCase = true) || responseStr.contains(":8001", ignoreCase = true)) {
                            val tv = probeSpecificIp(ip)
                            if (tv != null) onDeviceDiscovered(tv)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Socket read timed out, continue receiving until overall deadline
                    } catch (e: Exception) {
                        Log.w(TAG, "SSDP receive loop error: ${e.message}")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SSDP scanner error: ${e.message}")
        } finally {
            try { multicastLock?.release() } catch (_: Exception) {}
        }
    }
}
