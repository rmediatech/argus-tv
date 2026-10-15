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
        val protocol = matchSsdp(payload) ?: return null
        return protocol.probeSsdp(host, port, payload) ?: protocol.probe(host, port)
    }

    /**
     * Probes an arbitrary host and port by consulting registered protocols that monitor that port.
     */
    suspend fun probeHost(host: String, port: Int): RemoteDevice? {
        val candidates = protocols.values.filter { it.signature.candidatePorts.contains(port) }
        val toTry = candidates.ifEmpty { protocols.values }
        for (protocol in toTry) {
            val dev = protocol.probe(host, port)
            if (dev != null) return dev
        }
        return null
    }

    suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> {
        val protocol = get(device.deviceType)
        return protocol.sendKey(device, key)
    }

    suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> {
        val protocol = get(device.deviceType)
        return protocol.launchApp(device, appName)
    }
}
