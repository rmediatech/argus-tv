package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey

/**
 * Protocol Signature:
 * Encapsulates discovery metadata for dynamic SSDP multicast and port probe aggregation.
 * Zero hardcoded strings or port lists in the scanner engine!
 */
data class ProtocolSignature(
    val protocolId: String,
    val deviceType: DeviceType,
    val ssdpSearchTargets: List<String> = emptyList(),
    val ssdpHeaderMatchers: List<String> = emptyList(),
    val candidatePorts: List<Int> = emptyList()
)

/**
 * Sovereign Agnostic Smart TV Protocol Interface:
 * Decouples transport wire protocols (ECP, DIAL, ADB, UPnP, WebSockets) from discovery & UI.
 */
interface SmartTvProtocol {
    val protocolId: String
    val deviceType: DeviceType
    val defaultPort: Int
    val signature: ProtocolSignature

    /**
     * Probes an unknown host and port to determine if it speaks this protocol.
     * Host may be an IPv4 address, IPv6 address, or hostname.
     */
    suspend fun probe(host: String, port: Int = defaultPort): RemoteDevice?

    /**
     * Inspects an incoming SSDP M-SEARCH response or NOTIFY packet.
     * Return non-null RemoteDevice if this protocol recognizes and resolves the device.
     */
    suspend fun probeSsdp(host: String, port: Int, ssdpPayload: String): RemoteDevice? = null

    suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit>

    suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit>
}
