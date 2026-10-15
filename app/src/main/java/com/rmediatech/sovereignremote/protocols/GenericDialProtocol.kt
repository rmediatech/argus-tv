package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Universal Sovereign Open DIAL & UPnP Smart TV Protocol:
 * Agnostic discovery and control for Samsung, LG, Sony, Vizio, and standard DIAL/UPnP MediaRenderer displays.
 */
class GenericDialProtocol : SmartTvProtocol {
    override val protocolId: String = "dial_upnp"
    override val deviceType: DeviceType = DeviceType.GENERIC
    override val defaultPort: Int = 8008

    override val signature = ProtocolSignature(
        protocolId = protocolId,
        deviceType = deviceType,
        ssdpSearchTargets = listOf(
            "urn:dial-multiscreen-org:service:dial:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "ssdp:all"
        ),
        ssdpHeaderMatchers = listOf(
            "dial",
            "mediarenderer",
            "samsung",
            "tizen",
            "webos",
            "vizio",
            ":8001",
            ":8080"
        ),
        candidatePorts = listOf(8008, 8001, 8080)
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .writeTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .build()

    override suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // DIAL App Launch mapping
            val appTarget = when (key) {
                RemoteKey.HOME -> "YouTube"
                RemoteKey.PLAY_PAUSE -> "YouTube"
                else -> null
            }
            if (appTarget != null) {
                return@withContext launchApp(device, appTarget)
            }
            Result.failure(IOException("Direct keyevent requires native TV pairing"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${device.httpBaseUrl}/apps/$appName"
            val request = Request.Builder().url(url).post("".toRequestBody()).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 201) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("DIAL launch returned ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun probe(host: String, port: Int): RemoteDevice? = withContext(Dispatchers.IO) {
        val candidatePaths = listOf("/dd.xml", "/setup/eureka_info", "/upnp/desc.xml", "/")
        val formattedHost = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host

        for (path in candidatePaths) {
            try {
                val url = "http://$formattedHost:$port$path"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        if (body.contains("<device>") || body.contains("friendlyName") || body.contains("name")) {
                            val nameMatch = Regex("<friendlyName>(.*?)</friendlyName>").find(body)
                                ?: Regex(""""(?:name|device_name)"\s*:\s*"([^"]+)"""").find(body)
                            val mfgMatch = Regex("<manufacturer>(.*?)</manufacturer>").find(body)
                                ?: Regex(""""manufacturer"\s*:\s*"([^"]+)"""").find(body)
                            val modelMatch = Regex("<modelName>(.*?)</modelName>").find(body)
                                ?: Regex(""""model_name"\s*:\s*"([^"]+)"""").find(body)

                            val mfg = mfgMatch?.groupValues?.get(1)?.trim() ?: ""
                            val name = nameMatch?.groupValues?.get(1)?.trim()?.ifBlank { "$mfg TV" } ?: "Smart TV"
                            val model = modelMatch?.groupValues?.get(1)?.trim() ?: mfg

                            val detectedType = when {
                                mfg.contains("Samsung", ignoreCase = true) -> DeviceType.SAMSUNG_TIZEN
                                mfg.contains("LG", ignoreCase = true) -> DeviceType.LG_WEBOS
                                mfg.contains("Roku", ignoreCase = true) -> DeviceType.ROKU
                                mfg.contains("Google", ignoreCase = true) || mfg.contains("Hisense", ignoreCase = true) -> DeviceType.GOOGLE_TV
                                else -> DeviceType.GENERIC
                            }

                            val mac = com.rmediatech.sovereignremote.net.WakeOnLan.getMacFromArp(host)

                            return@withContext RemoteDevice(
                                id = "dial_${detectedType.name.lowercase()}_$host",
                                name = name,
                                ipAddress = host,
                                port = port,
                                deviceType = detectedType,
                                modelName = model,
                                isEthernet = false,
                                macAddress = mac
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        null
    }

    override suspend fun probeSsdp(host: String, port: Int, ssdpPayload: String): RemoteDevice? {
        val locationMatch = Regex("""LOCATION:\s*(http://[^\r\n]+)""", RegexOption.IGNORE_CASE).find(ssdpPayload)
        val locationUrl = locationMatch?.groupValues?.get(1)
        if (locationUrl != null) {
            try {
                val uri = java.net.URI.create(locationUrl)
                val targetPort = if (uri.port > 0) uri.port else port
                return probe(host, targetPort)
            } catch (_: Exception) {}
        }
        return probe(host, port)
    }
}
