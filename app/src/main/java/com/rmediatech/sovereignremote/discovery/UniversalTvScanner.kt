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
import java.net.*
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

        val activeSubnetInfo = NetworkEnvironmentManager.getActiveSubnetInfo(context)
        val currentNetId = activeSubnetInfo?.networkId
        val currentSubnet = activeSubnetInfo?.subnetPrefix

        val candidateIps = NetworkEnvironmentManager.generateCandidateIps(activeSubnetInfo).toMutableSet()
        candidateIps.addAll(arpMappings.keys)

        val activeArpIps = arpMappings.keys.filter { ip ->
            currentSubnet == null || NetworkEnvironmentManager.matchesSubnet(ip, currentSubnet)
        }
        val gwIp = activeSubnetInfo?.gatewayIp
        val sortedCandidates = (activeArpIps + candidateIps).distinct().filter { ip ->
            gwIp.isNullOrBlank() || ip != gwIp
        }

        val scanStartTime = System.currentTimeMillis()
        SovereignLogger.i(
            SovereignLogger.Category.DISCOVERY,
            "Scan initiated: activeNetId=$currentNetId, subnet=$currentSubnet, candidates=${sortedCandidates.size}, timeout=${timeoutMs}ms"
        )

        withTimeoutOrNull(timeoutMs.toLong()) {
            coroutineScope {
                fun recordDevice(device: RemoteDevice) {
                    if (!gwIp.isNullOrBlank() && device.ipAddress == gwIp) return
                    val mac = if (device.macAddress.isNullOrBlank()) {
                        arpMappings[device.ipAddress] ?: WakeOnLan.getMacFromArp(device.ipAddress)
                    } else device.macAddress

                    val inferred = SmartTvClassifier.inferBrandFromMac(mac)
                    val effectiveDevice = if (device.deviceType == DeviceType.GENERIC && inferred != null) {
                        val isFire = inferred == DeviceType.FIRE_TV
                        device.copy(
                            deviceType = inferred,
                            port = if (isFire) 5555 else device.port,
                            name = if (isFire) "Fire TV Stick" else "${inferred.displayName} (${device.ipAddress})",
                            modelName = inferred.displayName,
                            macAddress = mac
                        )
                    } else device.copy(macAddress = mac)

                    val existing = foundDevices[effectiveDevice.ipAddress]
                    val shouldUpdate = existing == null ||
                        (existing.deviceType == DeviceType.GENERIC && effectiveDevice.deviceType != DeviceType.GENERIC) ||
                        (existing.port != 5555 && effectiveDevice.port == 5555)
                    if (shouldUpdate) {
                        val safeId = if (effectiveDevice.id.isBlank() || effectiveDevice.id.endsWith("_")) {
                            "${effectiveDevice.deviceType.name.lowercase()}_${effectiveDevice.ipAddress}"
                        } else effectiveDevice.id

                        val enriched = effectiveDevice.copy(
                            id = safeId,
                            subnetPrefix = effectiveDevice.subnetPrefix ?: currentSubnet,
                            networkId = effectiveDevice.networkId ?: currentNetId
                        )
                        foundDevices[enriched.ipAddress] = enriched
                        SovereignLogger.i(
                            SovereignLogger.Category.DISCOVERY,
                            "Resolved device: [${enriched.deviceType}] ${enriched.name} at ${enriched.ipAddress}:${enriched.port} (mac=${enriched.macAddress})"
                        )
                        onDeviceFound?.invoke(enriched)

                        if (enriched.deviceType == DeviceType.GENERIC) {
                            launch {
                                val deep = SmartTvClassifier.deepInquireHost(enriched.ipAddress, enriched.macAddress, enriched.port, registry)
                                if (deep.deviceType != DeviceType.GENERIC) {
                                    recordDevice(deep.copy(subnetPrefix = currentSubnet, networkId = currentNetId))
                                }
                            }
                        }
                    }
                }

                activeArpIps.forEach { ip ->
                    val mac = arpMappings[ip]
                    val brand = SmartTvClassifier.inferBrandFromMac(mac)
                    if (brand != null) {
                        launch {
                            val deep = SmartTvClassifier.deepInquireHost(ip, mac, if (brand == DeviceType.FIRE_TV) 5555 else 8008, registry)
                            recordDevice(deep)
                        }
                    }
                }

                launch {
                    discoverSsdp(
                        timeoutMs = (timeoutMs * 0.85).toInt(),
                        broadcastIp = activeSubnetInfo?.broadcastIp,
                        onDeviceDiscovered = { dev -> recordDevice(dev) }
                    )
                }

                val candidatePorts = registry.getAllCandidatePorts()
                val concurrencyLimit = Semaphore(35)

                sortedCandidates.forEach { ip ->
                    launch {
                        concurrencyLimit.withPermit {
                            for (port in candidatePorts) {
                                val current = foundDevices[ip]
                                if (current != null && current.deviceType != DeviceType.GENERIC && current.port == 5555) break
                                if (isPortReachable(ip, port, 180)) {
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
        SovereignLogger.i(SovereignLogger.Category.DISCOVERY, "Scan finished in ${elapsed}ms. Total devices discovered: ${foundDevices.size}")

        val latestArp = WakeOnLan.getAllArpMappings()
        foundDevices.values.map { dev ->
            if (dev.macAddress.isNullOrBlank()) dev.copy(macAddress = latestArp[dev.ipAddress]) else dev
        }.sortedBy { it.name }
    }

    suspend fun probeSpecificIp(ip: String): RemoteDevice? = withContext(Dispatchers.IO) {
        val activeNet = NetworkEnvironmentManager.getActiveSubnetInfo(context)
        val mac = WakeOnLan.getMacFromArp(ip)

        val deepResolved = SmartTvClassifier.deepInquireHost(ip, mac, 8008, registry)
        if (deepResolved.deviceType != DeviceType.GENERIC) {
            return@withContext deepResolved.copy(
                subnetPrefix = deepResolved.subnetPrefix ?: activeNet?.subnetPrefix,
                networkId = deepResolved.networkId ?: activeNet?.networkId
            )
        }

        val openPorts = registry.getAllCandidatePorts().filter { isPortReachable(ip, it, 200) }
        if (openPorts.isNotEmpty()) {
            SmartTvClassifier.probeAndConfirm(ip, openPorts, registry)?.let { classified ->
                return@withContext classified.device.copy(
                    macAddress = classified.device.macAddress ?: mac,
                    subnetPrefix = classified.device.subnetPrefix ?: activeNet?.subnetPrefix,
                    networkId = classified.device.networkId ?: activeNet?.networkId
                )
            }
        }

        deepResolved.copy(subnetPrefix = activeNet?.subnetPrefix, networkId = activeNet?.networkId)
    }

    private fun isPortReachable(ip: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs); true }
    } catch (_: Exception) { false }

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

            val socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 400
            }
            coroutineContext.job.invokeOnCompletion { try { socket.close() } catch (_: Exception) {} }

            try {
                val targets = mutableListOf<InetAddress>()
                try { targets.add(InetAddress.getByName("239.255.255.250")) } catch (_: Exception) {}
                if (!broadcastIp.isNullOrBlank()) {
                    try { targets.add(InetAddress.getByName(broadcastIp)) } catch (_: Exception) {}
                }

                val stQueries = registry.getAllSsdpSearchTargets().ifEmpty {
                    listOf("ssdp:all", "urn:dial-multiscreen-org:service:dial:1", "roku:ecp")
                }

                for (st in stQueries) {
                    val bytes = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nST: $st\r\nMX: 2\r\n\r\n".toByteArray(Charsets.UTF_8)
                    for (target in targets) {
                        try {
                            socket.send(DatagramPacket(bytes, bytes.size, target, 1900))
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

                        if (responseStr.contains("InternetGatewayDevice", ignoreCase = true) ||
                            responseStr.contains("WANConnectionDevice", ignoreCase = true) ||
                            responseStr.contains("WANDevice", ignoreCase = true) ||
                            ip.endsWith(".1")
                        ) {
                            val uuid = Regex("""uuid:([a-f0-9-]+)""", RegexOption.IGNORE_CASE).find(responseStr)?.groupValues?.get(1)
                            if (!uuid.isNullOrBlank()) {
                                NetworkEnvironmentManager.recordGatewayHardwareId(context, ip, uuid)
                            }
                        }

                        launch {
                            registry.dispatchSsdp(ip, 8008, responseStr)?.let { onDeviceDiscovered(it) }
                        }
                    } catch (_: SocketTimeoutException) {
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
