package com.rmediatech.sovereignremote.protocols

import android.content.Context
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey

/**
 * Sovereign TV Protocol Registry:
 * One Job: Central agnostic registry and dynamic dispatcher for all supported TV & media protocols.
 * Aggregates discovery signatures and routes commands dynamically without hardcoding vendor logic.
 */
class TvProtocolRegistry(private val context: Context? = null) {
    private val protocols = mutableMapOf<String, SmartTvProtocol>()
    private val deviceTypeMap = mutableMapOf<DeviceType, SmartTvProtocol>()

    init {
        register(RokuProtocol())
        register(SamsungProtocol())
        register(LgWebOsProtocol())
        register(SonyBraviaProtocol())
        register(VizioSmartCastProtocol())
        register(GoogleTvProtocol(context))
        register(FireTvProtocol(context))
        register(GenericDialProtocol())
    }

    fun register(protocol: SmartTvProtocol) {
        protocols[protocol.protocolId] = protocol
        deviceTypeMap[protocol.deviceType] = protocol
    }

    fun get(deviceType: DeviceType): SmartTvProtocol {
        return deviceTypeMap[deviceType]
            ?: protocols["dial_upnp"]
            ?: protocols.values.first()
    }

    fun getById(protocolId: String): SmartTvProtocol? {
        return protocols[protocolId]
    }

    fun getProtocols(): List<SmartTvProtocol> = protocols.values.toList()

    fun getAllProtocols(): List<SmartTvProtocol> = protocols.values.toList()

    /**
     * Aggregates all unique SSDP M-SEARCH ST queries across all registered protocols dynamically.
     * Zero hardcoded queries in the scanner engine!
     */
    fun getAllSsdpSearchTargets(): List<String> {
        val targets = mutableListOf<String>()
        for (protocol in protocols.values) {
            for (st in protocol.signature.ssdpSearchTargets) {
                if (!targets.contains(st)) targets.add(st)
            }
        }
        return targets
    }

    /**
     * Aggregates all candidate TCP ports across all registered protocols dynamically.
     */
    fun getAllCandidatePorts(): List<Int> {
        val ports = mutableListOf<Int>()
        for (protocol in protocols.values) {
            for (p in protocol.signature.candidatePorts) {
                if (!ports.contains(p)) ports.add(p)
            }
        }
        return ports
    }

    /**
     * Returns all registered protocols that monitor the specified candidate port.
     */
    fun getProtocolsForPort(port: Int): List<SmartTvProtocol> {
        return protocols.values.filter { it.signature.candidatePorts.contains(port) }
    }

    /**
     * Inspects an incoming SSDP packet and matches the protocol that recognizes it.
     */
    fun matchSsdp(payload: String): SmartTvProtocol? {
        for (protocol in protocols.values) {
            val matches = protocol.signature.ssdpHeaderMatchers.any {
                payload.contains(it, ignoreCase = true)
            }
            if (matches) return protocol
        }
        return null
    }

    /**
     * Inspects an incoming SSDP packet and dispatches to the protocol that recognizes it.
     */
    suspend fun dispatchSsdp(host: String, port: Int, payload: String): RemoteDevice? {
        val classified = com.rmediatech.sovereignremote.discovery.SmartTvClassifier.classifyFromSsdp(host, port, payload, this)
        if (classified != null) return classified.device

        val protocol = matchSsdp(payload) ?: return null
        val dev = protocol.probeSsdp(host, port, payload) ?: protocol.probe(host, port)
        return dev?.let { com.rmediatech.sovereignremote.discovery.SmartTvClassifier.sanitizeDevice(it) }
    }

    /**
     * Probes an arbitrary host and port by consulting registered protocols that monitor that port.
     */
    suspend fun probeHost(host: String, port: Int): RemoteDevice? {
        val candidates = getProtocolsForPort(port).ifEmpty { protocols.values }
        for (protocol in candidates) {
            val dev = protocol.probe(host, port)
            if (dev != null) return com.rmediatech.sovereignremote.discovery.SmartTvClassifier.sanitizeDevice(dev)
        }
        return null
    }

    data class KeyDispatchResult(
        val isSuccess: Boolean,
        val elapsedMs: Long,
        val promotedDevice: RemoteDevice? = null,
        val error: Throwable? = null
    )

    suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> {
        val protocol = get(device.deviceType)
        return protocol.sendKey(device, key)
    }

    /**
     * Sovereign Polymorphic Key Dispatcher:
     * Attempts the assigned protocol first. If it fails or if the device is GENERIC,
     * it initiates Agnostic Protocol Bombardment across all registered protocol engines
     * and auto-promotes the device to the winning protocol upon successful acknowledgment!
     */
    suspend fun sendKeyWithAutoPromotion(device: RemoteDevice, key: RemoteKey): KeyDispatchResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val primaryProtocol = get(device.deviceType)

        // 1. Try primary protocol if not generic
        if (device.deviceType != DeviceType.GENERIC) {
            val directResult = primaryProtocol.sendKey(device, key)
            if (directResult.isSuccess) {
                val elapsed = (System.currentTimeMillis() - t0).coerceAtLeast(1)
                return@withContext KeyDispatchResult(true, elapsed, null)
            }
        }

        // 2. Primary failed or device is GENERIC.
        // Activate Agnostic Protocol Failover Bombardment!
        com.rmediatech.sovereignremote.logging.SovereignLogger.w(
            com.rmediatech.sovereignremote.logging.SovereignLogger.Category.PROTOCOL,
            "Primary [${device.deviceType}] on ${device.ipAddress} did not ACK key $key. Activating Agnostic Protocol Failover Bombardment..."
        )

        val candidateProtocols = listOfNotNull(
            getById("firetv"),
            getById("googletv"),
            getById("roku_ecp"),
            getById("samsung_tizen"),
            getById("lg_webos"),
            getById("sony_bravia"),
            getById("vizio_smartcast")
        ).filter { it.protocolId != primaryProtocol.protocolId }

        val isAmazon = device.name.contains("Fire", ignoreCase = true) || 
            device.modelName?.contains("Fire", ignoreCase = true) == true ||
            com.rmediatech.sovereignremote.discovery.SmartTvClassifier.inferBrandFromMac(device.macAddress) == DeviceType.FIRE_TV

        for (candidate in candidateProtocols) {
            if (isAmazon && candidate.deviceType != DeviceType.FIRE_TV) continue

            val trialPorts = when (candidate.deviceType) {
                DeviceType.FIRE_TV -> listOf(5555)
                DeviceType.ROKU -> listOf(8060)
                DeviceType.SAMSUNG_TIZEN -> listOf(8001, 8002)
                DeviceType.LG_WEBOS -> listOf(3000, 3001)
                DeviceType.SONY_BRAVIA -> listOf(80, 20060)
                DeviceType.VIZIO_SMARTCAST -> listOf(7345, 9000)
                DeviceType.GOOGLE_TV -> if (isAmazon) emptyList() else listOf(8008, 5555)
                else -> listOf(candidate.defaultPort)
            }

            for (trialPort in trialPorts) {
                val trialDevice = device.copy(deviceType = candidate.deviceType, port = trialPort)
                val trialResult = candidate.sendKey(trialDevice, key)
                if (trialResult.isSuccess) {
                    val elapsed = (System.currentTimeMillis() - t0).coerceAtLeast(1)
                    val promoted = trialDevice.copy(
                        name = if (device.deviceType == DeviceType.GENERIC) "${candidate.deviceType.displayName} (${device.ipAddress})" else device.name,
                        modelName = candidate.deviceType.displayName
                    )
                    com.rmediatech.sovereignremote.logging.SovereignLogger.i(
                        com.rmediatech.sovereignremote.logging.SovereignLogger.Category.PROTOCOL,
                        "🎉 BOMBARDMENT SUCCESS! Protocol [${candidate.deviceType}] on port $trialPort ACKed key $key on ${device.ipAddress}! Auto-promoting."
                    )
                    return@withContext KeyDispatchResult(true, elapsed, promoted)
                }
            }
        }

        val elapsed = (System.currentTimeMillis() - t0).coerceAtLeast(1)
        KeyDispatchResult(false, elapsed, null, Exception("All candidate protocols failed on ${device.ipAddress}"))
    }

    suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> {
        val protocol = get(device.deviceType)
        return protocol.launchApp(device, appName)
    }
}
