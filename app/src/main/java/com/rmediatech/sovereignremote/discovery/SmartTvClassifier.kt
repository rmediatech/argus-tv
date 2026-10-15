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
