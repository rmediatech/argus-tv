package com.rmediatech.sovereignremote.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.rmediatech.sovereignremote.logging.SovereignLogger
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.net.NetworkEnvironmentManager
import com.rmediatech.sovereignremote.net.WakeOnLan
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
 * Agnostic, multi-protocol discovery engine.
 * Dynamically aggregates protocol SSDP signatures and port sets without hardcoded vendor logic.
 * Bounded by strict coroutine scope cancellation and 3500ms maximum time budget.
 */
class UniversalTvScanner(
    private val context: Context,
    private val registry: TvProtocolRegistry
) {
    companion object {
        private const val TAG = "UniversalTvScanner"
        const val DEFAULT_SCAN_TIMEOUT_MS = 3500
    }

    suspend fun discoverAll(
        timeoutMs: Int = DEFAULT_SCAN_TIMEOUT_MS,
        onDeviceFound: ((RemoteDevice) -> Unit)? = null
    ): List<RemoteDevice> = withContext(Dispatchers.IO) {
        val foundDevices = ConcurrentHashMap<String, RemoteDevice>()
        val arpMappings = WakeOnLan.getAllArpMappings()

        // 1. Resolve active network subnet environment
        val activeSubnetInfo = NetworkEnvironmentManager.getActiveSubnetInfo(context)
        val currentNetId = activeSubnetInfo?.networkId
        val currentSubnet = activeSubnetInfo?.subnetPrefix

        val candidateIps = NetworkEnvironmentManager.generateCandidateIps(activeSubnetInfo).toMutableSet()
        candidateIps.addAll(arpMappings.keys)

        // Prioritize hosts in kernel ARP cache (instant 0ms response probability)
        val sortedCandidates = mutableListOf<String>()
        val activeArpIps = arpMappings.keys.filter { ip ->
            currentSubnet == null || NetworkEnvironmentManager.matchesSubnet(ip, currentSubnet)
        }
        sortedCandidates.addAll(activeArpIps)
        for (ip in candidateIps) {
            if (!sortedCandidates.contains(ip)) sortedCandidates.add(ip)
        }

        val scanStartTime = System.currentTimeMillis()
        SovereignLogger.i(
            SovereignLogger.Category.DISCOVERY,
            "Scan initiated: activeNetId=$currentNetId, subnet=$currentSubnet, candidates=${sortedCandidates.size}, timeout=${timeoutMs}ms"
        )

        fun recordDevice(device: RemoteDevice) {
            val existing = foundDevices[device.ipAddress]
            val shouldUpdate = existing == null || 
                (existing.deviceType == DeviceType.GENERIC && device.deviceType != DeviceType.GENERIC) ||
                (existing.port != 5555 && device.port == 5555)
            if (shouldUpdate) {
                val safeId = if (device.id.isBlank() || device.id.endsWith("_")) "${device.deviceType.name.lowercase()}_${device.ipAddress}" else device.id
                val enriched = device.copy(
                    id = safeId,
                    macAddress = if (device.macAddress.isNullOrBlank()) {
                        arpMappings[device.ipAddress] ?: WakeOnLan.getMacFromArp(device.ipAddress)
                    } else device.macAddress,
                    subnetPrefix = device.subnetPrefix ?: currentSubnet,
                    networkId = device.networkId ?: currentNetId
                )
                foundDevices[device.ipAddress] = enriched
                SovereignLogger.i(
                    SovereignLogger.Category.DISCOVERY,
                    "Resolved device: [${enriched.deviceType}] ${enriched.name} at ${enriched.ipAddress}:${enriched.port} (mac=${enriched.macAddress})"
                )
                onDeviceFound?.invoke(enriched)
            }
        }

        // 2. Bound entire scan execution to strict timeout with coroutineScope
        // When timeoutMs fires, withTimeoutOrNull cancels coroutineScope and ALL child jobs immediately!
        withTimeoutOrNull(timeoutMs.toLong()) {
            coroutineScope {
                // High-speed Multi-Target SSDP Multicast & Subnet Broadcast
                launch {
                    discoverSsdp(
                        timeoutMs = (timeoutMs * 0.85).toInt(),
                        broadcastIp = activeSubnetInfo?.broadcastIp,
                        onDeviceDiscovered = { dev -> recordDevice(dev) }
                    )
                }

                // Dynamic candidate port sweep across ports registered by protocol registry
                val candidatePorts = registry.getAllCandidatePorts()
                val concurrencyLimit = Semaphore(35)

                sortedCandidates.forEach { ip ->
                    launch {
                        concurrencyLimit.withPermit {
                            for (port in candidatePorts) {
                                val current = foundDevices[ip]
                                if (current != null && current.deviceType != DeviceType.GENERIC && current.port == 5555) break
                                if (isPortReachable(ip, port, 120)) {
                                    val dev = registry.probeHost(ip, port)
                                    if (dev != null) {
                                        recordDevice(dev)
                                        if (dev.deviceType != DeviceType.GENERIC) break
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val elapsed = System.currentTimeMillis() - scanStartTime
        SovereignLogger.i(
            SovereignLogger.Category.DISCOVERY,
            "Scan finished in ${elapsed}ms. Total devices discovered: ${foundDevices.size}"
        )

        val latestArp = WakeOnLan.getAllArpMappings()
        foundDevices.values.map { dev ->
            if (dev.macAddress.isNullOrBlank()) {
                dev.copy(macAddress = latestArp[dev.ipAddress])
            } else {
                dev
            }
        }.sortedBy { it.name }
    }

    suspend fun probeSpecificIp(ip: String): RemoteDevice? = withContext(Dispatchers.IO) {
        val activeNet = NetworkEnvironmentManager.getActiveSubnetInfo(context)
        val mac = WakeOnLan.getMacFromArp(ip)
        val candidatePorts = registry.getAllCandidatePorts()
        val openPorts = candidatePorts.filter { isPortReachable(ip, it, 200) }

        if (openPorts.isNotEmpty()) {
            val classified = SmartTvClassifier.probeAndConfirm(ip, openPorts, registry)
            if (classified != null) {
                return@withContext classified.device.copy(
                    macAddress = classified.device.macAddress ?: mac,
                    subnetPrefix = classified.device.subnetPrefix ?: activeNet?.subnetPrefix,
                    networkId = classified.device.networkId ?: activeNet?.networkId
                )
            }
        }

        // Generic fallback if host responds on any HTTP port
        RemoteDevice(
            id = "generic_$ip",
            name = "Smart TV ($ip)",
            ipAddress = ip,
            port = 8008,
            deviceType = DeviceType.GENERIC,
            macAddress = mac,
            subnetPrefix = activeNet?.subnetPrefix,
            networkId = activeNet?.networkId
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

            val socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 400

            coroutineContext.job.invokeOnCompletion {
                try { socket.close() } catch (_: Exception) {}
            }

            try {
                val targets = mutableListOf<InetAddress>()
                try { targets.add(InetAddress.getByName("239.255.255.250")) } catch (_: Exception) {}
                if (!broadcastIp.isNullOrBlank()) {
                    try { targets.add(InetAddress.getByName(broadcastIp)) } catch (_: Exception) {}
                }

                // Gather SSDP search targets dynamically from registry
                val stQueries = registry.getAllSsdpSearchTargets().ifEmpty {
                    listOf("ssdp:all", "urn:dial-multiscreen-org:service:dial:1", "roku:ecp")
                }

                // Transmit SSDP search requests
                for (st in stQueries) {
                    val query = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nST: $st\r\nMX: 2\r\n\r\n"
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

                        launch {
                            val dev = registry.dispatchSsdp(ip, 8008, responseStr)
                            if (dev != null) {
                                onDeviceDiscovered(dev)
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Socket read timed out, continue receiving until overall deadline
                    } catch (e: Exception) {
                        Log.w(TAG, "SSDP receive loop error: ${e.message}")
                        break
                    }
                }
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "SSDP scanner error: ${e.message}")
        } finally {
            try { multicastLock?.release() } catch (_: Exception) {}
        }
    }
}
