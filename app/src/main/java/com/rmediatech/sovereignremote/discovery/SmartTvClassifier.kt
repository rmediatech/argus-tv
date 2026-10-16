package com.rmediatech.sovereignremote.discovery

import com.rmediatech.sovereignremote.logging.SovereignLogger
import com.rmediatech.sovereignremote.model.DeviceSerializer
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.protocols.SmartTvProtocol
import com.rmediatech.sovereignremote.protocols.TvProtocolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal Sovereign Smart TV Classifier & Fingerprint Engine:
 * One-Job-Principle (OJP): Reusable global engine that interrogates, classifies,
 * and confirms unknown network hosts and banners into verified Smart TV protocols.
 * Guarantees zero-panic execution and resilience against corrupted frames.
 */
object SmartTvClassifier {

    enum class Confidence {
        HIGH,    // Confirmed via protocol-specific JSON/XML payload or API response
        MEDIUM,  // Confirmed via SSDP header / banner match
        LOW      // Inferred solely from open TCP port footprint
    }

    data class ClassificationResult(
        val deviceType: DeviceType,
        val protocolId: String,
        val confidence: Confidence,
        val device: RemoteDevice
    )

    /**
     * Inspects an incoming SSDP announcement or discovery response and classifies it dynamically.
     */
    suspend fun classifyFromSsdp(
        host: String,
        port: Int,
        payload: String,
        registry: TvProtocolRegistry
    ): ClassificationResult? = withContext(Dispatchers.IO) {
        if (payload.isBlank() || host.isBlank()) return@withContext null

        val lower = payload.lowercase()
        val protocols = registry.getAllProtocols()

        // 1. Direct protocol signature matcher
        for (proto in protocols) {
            val matches = proto.signature.ssdpHeaderMatchers.any { lower.contains(it) }
            if (matches) {
                try {
                    val resolved = proto.probeSsdp(host, port, payload) ?: proto.probe(host, port)
                    if (resolved != null) {
                        return@withContext ClassificationResult(
                            deviceType = resolved.deviceType,
                            protocolId = proto.protocolId,
                            confidence = Confidence.HIGH,
                            device = sanitizeDevice(resolved)
                        )
                    }
                } catch (e: Exception) {
                    SovereignLogger.w(SovereignLogger.Category.DISCOVERY, "SSDP probe failed for ${proto.protocolId}: ${e.message}")
                }
            }
        }

        null
    }

    /**
     * Interrogates an arbitrary host across candidate ports in deterministic priority order.
     * Identifies the exact vendor protocol and returns a confirmed RemoteDevice.
     */
    suspend fun probeAndConfirm(
        host: String,
        openPorts: List<Int>,
        registry: TvProtocolRegistry
    ): ClassificationResult? = withContext(Dispatchers.IO) {
        if (host.isBlank() || openPorts.isEmpty()) return@withContext null

        // 1. Prioritize specialized native TV ports over generic ports
        val priorityPorts = listOf(8060, 8001, 8002, 3000, 3001, 7345, 20060, 5555, 8008, 80)
        val sortedPorts = openPorts.sortedBy { p ->
            val idx = priorityPorts.indexOf(p)
            if (idx >= 0) idx else 99
        }

        for (port in sortedPorts) {
            val candidates = registry.getProtocolsForPort(port)
            for (proto in candidates) {
                try {
                    val dev = proto.probe(host, port)
                    if (dev != null && dev.deviceType != DeviceType.GENERIC) {
                        return@withContext ClassificationResult(
                            deviceType = dev.deviceType,
                            protocolId = proto.protocolId,
                            confidence = Confidence.HIGH,
                            device = sanitizeDevice(dev)
                        )
                    }
                } catch (e: Exception) {
                    SovereignLogger.w(SovereignLogger.Category.DISCOVERY, "Probe error on $host:$port (${proto.protocolId}): ${e.message}")
                }
            }
        }

        // 2. Generic fallback probe if specialized vendor probes didn't match
        for (port in sortedPorts) {
            val generic = registry.getById("dial_upnp")
            if (generic != null) {
                try {
                    val dev = generic.probe(host, port)
                    if (dev != null) {
                        return@withContext ClassificationResult(
                            deviceType = dev.deviceType,
                            protocolId = generic.protocolId,
                            confidence = if (dev.deviceType != DeviceType.GENERIC) Confidence.HIGH else Confidence.MEDIUM,
                            device = sanitizeDevice(dev)
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        null
    }

    /**
     * Infers known smart TV manufacturer brand from IEEE MAC OUI prefix.
     */
    fun inferBrandFromMac(mac: String?): DeviceType? {
        if (mac.isNullOrBlank()) return null
        val clean = mac.replace(":", "").replace("-", "").uppercase()
        if (clean.length < 6) return null
        val prefix = clean.substring(0, 6)

        return when {
            // Amazon Technologies Inc. (Fire TV Sticks, Cubes, Omnis)
            prefix in setOf("68F63B", "2873F6", "00FC8B", "AC63BE", "FC65DE", "B47C9C", "50DCE7", "44650D", "38F73D", "0C47C9", "40B4CD", "74C246", "F0D2F1", "F08173", "6837E9", "6854FD", "689A87", "68B691", "68DBF5", "34D270", "84D6D0") -> DeviceType.FIRE_TV
            // Roku, Inc. (Streaming sticks, Roku TVs)
            prefix in setOf("B0A737", "D83134", "DC3A5E", "208984", "84EAED", "ACAE19", "C83A35", "080581", "CC6DA0", "7831C1", "2C1A31", "A81B5A", "A0143D") -> DeviceType.ROKU
            // Samsung Electronics (Tizen OS)
            prefix in setOf("E47CF9", "4844F7", "0012FB", "BC1485", "CC07AB", "0007AB", "508569", "641CAE", "702C1F", "9439E5", "B8B98E", "E4E0C5", "F40343") -> DeviceType.SAMSUNG_TIZEN
            // LG Electronics (webOS)
            prefix in setOf("10F920", "203DB2", "A823FE", "CC2D8C", "40B0FA", "58A2B5", "74A722", "BCF5AC", "E85B5B", "001C62", "C4366C", "A039EE") -> DeviceType.LG_WEBOS
            // Sony Corporation (BRAVIA)
            prefix in setOf("0013A9", "F0BF97", "0024BE", "FC0FE6", "00014A", "AC9B0A", "3CD92B", "544249", "709E29") -> DeviceType.SONY_BRAVIA
            // Vizio, Inc. (SmartCast)
            prefix in setOf("0019FB", "340286", "ECADB8", "00085B", "A48D3B") -> DeviceType.VIZIO_SMARTCAST
            else -> null
        }
    }

    /**
     * Sovereign Deep Inquisitor:
     * Bombards an arbitrary host across known and unknown protocol signatures,
     * cross-examining MAC OUI, open ports, and live API responses to promote
     * generic placeholders into verified TV controllers.
     */
    suspend fun deepInquireHost(
        host: String,
        mac: String?,
        discoveredPort: Int,
        registry: TvProtocolRegistry
    ): RemoteDevice = withContext(Dispatchers.IO) {
        val inferredBrand = inferBrandFromMac(mac)

        // 1. Android/Dadb probe (Port 5555)
        if (isPortReachable(host, 5555, 180) || inferredBrand == DeviceType.FIRE_TV) {
            val fireTvProto = registry.getById("firetv")
            val googleTvProto = registry.getById("googletv")

            if (inferredBrand == DeviceType.FIRE_TV) {
                val dev = fireTvProto?.probe(host, 5555)
                return@withContext sanitizeDevice(dev ?: RemoteDevice(
                    id = "firetv_$host",
                    name = "Fire TV Stick",
                    ipAddress = host,
                    port = 5555,
                    deviceType = DeviceType.FIRE_TV,
                    modelName = "Amazon Fire TV",
                    macAddress = mac
                ))
            }

            val fireDev = fireTvProto?.probe(host, 5555)
            if (fireDev != null && (fireDev.modelName?.contains("Fire", ignoreCase = true) == true || fireDev.name.contains("Fire", ignoreCase = true))) {
                return@withContext sanitizeDevice(fireDev.copy(macAddress = mac ?: fireDev.macAddress))
            }

            val googleDev = googleTvProto?.probe(host, 8008) ?: googleTvProto?.probe(host, 5555)
            if (googleDev != null) {
                return@withContext sanitizeDevice(googleDev.copy(macAddress = mac ?: googleDev.macAddress))
            }

            val targetType = if (inferredBrand == DeviceType.FIRE_TV) DeviceType.FIRE_TV else DeviceType.GOOGLE_TV
            return@withContext sanitizeDevice(RemoteDevice(
                id = "${targetType.name.lowercase()}_$host",
                name = targetType.displayName,
                ipAddress = host,
                port = 5555,
                deviceType = targetType,
                modelName = targetType.displayName,
                macAddress = mac
            ))
        }

        // 2. Roku ECP (Port 8060)
        if (isPortReachable(host, 8060, 180) || inferredBrand == DeviceType.ROKU) {
            val dev = registry.getById("roku_ecp")?.probe(host, 8060)
            if (dev != null) return@withContext sanitizeDevice(dev.copy(macAddress = mac ?: dev.macAddress))
        }

        // 3. Samsung Tizen (Ports 8001 / 8002)
        if (isPortReachable(host, 8001, 180) || isPortReachable(host, 8002, 180) || inferredBrand == DeviceType.SAMSUNG_TIZEN) {
            val dev = registry.getById("samsung_tizen")?.let { it.probe(host, 8001) ?: it.probe(host, 8002) }
            if (dev != null) return@withContext sanitizeDevice(dev.copy(macAddress = mac ?: dev.macAddress))
        }

        // 4. LG webOS (Ports 3000 / 3001)
        if (isPortReachable(host, 3000, 180) || isPortReachable(host, 3001, 180) || inferredBrand == DeviceType.LG_WEBOS) {
            val dev = registry.getById("lg_webos")?.let { it.probe(host, 3000) ?: it.probe(host, 3001) }
            if (dev != null) return@withContext sanitizeDevice(dev.copy(macAddress = mac ?: dev.macAddress))
        }

        // 5. Sony BRAVIA (Ports 80 / 20060)
        if (isPortReachable(host, 80, 180) || isPortReachable(host, 20060, 180) || inferredBrand == DeviceType.SONY_BRAVIA) {
            val dev = registry.getById("sony_bravia")?.let { it.probe(host, 80) ?: it.probe(host, 20060) }
            if (dev != null) return@withContext sanitizeDevice(dev.copy(macAddress = mac ?: dev.macAddress))
        }

        // 6. Vizio SmartCast (Ports 7345 / 9000)
        if (isPortReachable(host, 7345, 180) || isPortReachable(host, 9000, 180) || inferredBrand == DeviceType.VIZIO_SMARTCAST) {
            val dev = registry.getById("vizio_smartcast")?.let { it.probe(host, 7345) ?: it.probe(host, 9000) }
            if (dev != null) return@withContext sanitizeDevice(dev.copy(macAddress = mac ?: dev.macAddress))
        }

        // 7. Google Cast / DIAL (Port 8008)
        if (isPortReachable(host, 8008, 180)) {
            val dev = registry.getById("googletv")?.probe(host, 8008)
            if (dev != null) return@withContext sanitizeDevice(dev.copy(macAddress = mac ?: dev.macAddress))
        }

        // 8. OUI fallback if brand was inferred
        if (inferredBrand != null) {
            return@withContext sanitizeDevice(RemoteDevice(
                id = "${inferredBrand.name.lowercase()}_$host",
                name = "${inferredBrand.displayName} ($host)",
                ipAddress = host,
                port = discoveredPort,
                deviceType = inferredBrand,
                modelName = inferredBrand.displayName,
                macAddress = mac
            ))
        }

        // 9. Generic fallback
        sanitizeDevice(RemoteDevice(
            id = "generic_$host",
            name = "Smart TV ($host)",
            ipAddress = host,
            port = discoveredPort,
            deviceType = DeviceType.GENERIC,
            macAddress = mac
        ))
    }

    private fun isPortReachable(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sanitizes external strings, neutralizing formula injection and bounding field lengths.
     */
    fun sanitizeDevice(device: RemoteDevice): RemoteDevice {
        val cleanName = DeviceSerializer.sanitize(device.name)
            .take(64)
            .ifBlank { "${device.deviceType.displayName} (${device.ipAddress})" }
        val cleanModel = DeviceSerializer.sanitize(device.modelName).take(64)
        val cleanId = if (device.id.isBlank() || device.id.endsWith("_")) {
            "${device.deviceType.name.lowercase()}_${device.ipAddress}"
        } else {
            device.id
        }

        return device.copy(
            id = cleanId,
            name = cleanName,
            modelName = cleanModel.ifBlank { device.deviceType.displayName }
        )
    }
}
