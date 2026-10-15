package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.logging.SovereignLogger
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.net.WakeOnLan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sovereign Sony BRAVIA Smart TV Protocol Engine:
 * Implements native Sony IRCC-over-HTTP REST protocol on port 80 / 20060
 * with Pre-Shared Key (PSK) authentication and zero cloud telemetry.
 */
class SonyBraviaProtocol(private val client: OkHttpClient? = null) : SmartTvProtocol {

    override val protocolId: String = "sony_bravia"
    override val deviceType: DeviceType = DeviceType.SONY_BRAVIA
    override val defaultPort: Int = 80

    override val signature = ProtocolSignature(
        protocolId = protocolId,
        deviceType = deviceType,
        ssdpSearchTargets = listOf(
            "urn:schemas-sony-com:service:IRCC:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1"
        ),
        ssdpHeaderMatchers = listOf(
            "sony",
            "bravia",
            "ircc",
            ":20060"
        ),
        candidatePorts = listOf(80, 20060)
    )

    private val httpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(600, TimeUnit.MILLISECONDS)
        .writeTimeout(600, TimeUnit.MILLISECONDS)
        .readTimeout(600, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun probe(host: String, port: Int): RemoteDevice? = withContext(Dispatchers.IO) {
        val targetPort = if (port == 20060) 20060 else 80
        val url = "http://${formatHost(host)}:$targetPort/sony/system"

        try {
            val reqBody = """{"method":"getSystemInformation","params":[],"id":1,"version":"1.0"}"""
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("X-Auth-PSK", "0000")
                .post(reqBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 403 || response.code == 401) {
                    val mac = WakeOnLan.getMacFromArp(host)
                    RemoteDevice(
                        id = "sony_$host",
                        name = "Sony BRAVIA",
                        ipAddress = host,
                        port = targetPort,
                        deviceType = DeviceType.SONY_BRAVIA,
                        modelName = "Sony BRAVIA 4K",
                        isEthernet = false,
                        macAddress = mac
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun probeSsdp(host: String, port: Int, ssdpPayload: String): RemoteDevice? {
        val lower = ssdpPayload.lowercase()
        if (lower.contains("sony") || lower.contains("bravia") || lower.contains("ircc:1")) {
            return probe(host, 80) ?: probe(host, 20060)
        }
        return null
    }

    override suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> = withContext(Dispatchers.IO) {
        val irccCode = mapToIrccCode(key)
            ?: return@withContext Result.failure(IllegalArgumentException("Unsupported Sony key: $key"))

        val url = "http://${device.formattedHost}:${device.port}/sony/ircc"
        val soapBody = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1">
                        <IRCCCode>$irccCode</IRCCCode>
                    </u:X_SendIRCC>
                </s:Body>
            </s:Envelope>
        """.trimIndent().toRequestBody("text/xml; charset=UTF-8".toMediaType())

        try {
            val request = Request.Builder()
                .url(url)
                .header("X-Auth-PSK", "0000")
                .header("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
                .post(soapBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Sony IRCC error: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            SovereignLogger.w(SovereignLogger.Category.PROTOCOL, "Sony sendKey($key) failed on ${device.ipAddress}: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Sony DIAL/App launch fallback
        val url = "http://${device.formattedHost}:8008/apps/$appName"
        try {
            val request = Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()
            httpClient.newCall(request).execute().use { res ->
                if (res.isSuccessful || res.code == 201) Result.success(Unit) else Result.failure(IOException("Launch code: ${res.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapToIrccCode(key: RemoteKey): String? = when (key) {
        RemoteKey.POWER -> "AAAAAQAAAAEAAAAVAw=="
        RemoteKey.UP -> "AAAAAQAAAAEAAAB0Aw=="
        RemoteKey.DOWN -> "AAAAAQAAAAEAAAB1Aw=="
        RemoteKey.LEFT -> "AAAAAQAAAAEAAAA0Aw=="
        RemoteKey.RIGHT -> "AAAAAQAAAAEAAAAzAw=="
        RemoteKey.SELECT -> "AAAAAQAAAAEAAABlAw=="
        RemoteKey.HOME -> "AAAAAQAAAAEAAABgAw=="
        RemoteKey.BACK -> "AAAAAQAAAAEAAABjAw=="
        RemoteKey.VOLUME_UP -> "AAAAAQAAAAEAAAASAw=="
        RemoteKey.VOLUME_DOWN -> "AAAAAQAAAAEAAAATAw=="
        RemoteKey.MUTE -> "AAAAAQAAAAEAAAAUAw=="
        RemoteKey.PLAY_PAUSE -> "AAAAAgAAAJcAAAAaAw=="
        RemoteKey.REWIND -> "AAAAAgAAAJcAAAAbAw=="
        RemoteKey.FAST_FORWARD -> "AAAAAgAAAJcAAAAcAw=="
        RemoteKey.INFO -> "AAAAAQAAAAEAAABLAw=="
    }

    private fun formatHost(host: String): String {
        return if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
    }
}
