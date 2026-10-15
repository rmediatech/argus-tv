package com.rmediatech.sovereignremote.protocols

import android.util.Base64
import com.rmediatech.sovereignremote.logging.SovereignLogger
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.net.WakeOnLan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Sovereign Samsung Smart TV (Tizen OS 2016+) Protocol Engine:
 * Implements native Samsung WebSocket remote control on ports 8001 (ws) and 8002 (wss)
 * and REST API v2 device introspection with zero cloud telemetry.
 */
class SamsungProtocol(private val client: OkHttpClient? = null) : SmartTvProtocol {

    override val protocolId: String = "samsung_tizen"
    override val deviceType: DeviceType = DeviceType.SAMSUNG_TIZEN
    override val defaultPort: Int = 8001

    override val signature = ProtocolSignature(
        protocolId = protocolId,
        deviceType = deviceType,
        ssdpSearchTargets = listOf(
            "urn:samsung.com:device:RemoteControlReceiver:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1"
        ),
        ssdpHeaderMatchers = listOf(
            "samsung",
            "tizen",
            ":8001",
            ":8002",
            "sec-rcr"
        ),
        candidatePorts = listOf(8001, 8002)
    )

    private val httpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(600, TimeUnit.MILLISECONDS)
        .writeTimeout(600, TimeUnit.MILLISECONDS)
        .readTimeout(600, TimeUnit.MILLISECONDS)
        .build()

    // Key cache for active WebSocket connections per IP
    private val activeWebSockets = ConcurrentHashMap<String, WebSocket>()

    override suspend fun probe(host: String, port: Int): RemoteDevice? = withContext(Dispatchers.IO) {
        val targetPort = if (port == 8002) 8002 else 8001
        val formattedHost = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
        val url = "http://$formattedHost:$targetPort/api/v2/"

        try {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val deviceObj = json.optJSONObject("device")

                    val rawName = deviceObj?.optString("name").orEmpty().ifBlank {
                        json.optString("name", "Samsung Smart TV")
                    }
                    val modelName = deviceObj?.optString("modelName").orEmpty().ifBlank {
                        json.optString("modelName", "Samsung Tizen TV")
                    }
                    val mac = deviceObj?.optString("wifiMac")?.lowercase()
                        ?: WakeOnLan.getMacFromArp(host)
                    val id = deviceObj?.optString("id")?.ifBlank { "samsung_$host" } ?: "samsung_$host"

                    RemoteDevice(
                        id = id,
                        name = rawName,
                        ipAddress = host,
                        port = targetPort,
                        deviceType = DeviceType.SAMSUNG_TIZEN,
                        modelName = modelName,
                        isEthernet = deviceObj?.optString("networkType") == "ethernet",
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
        if (lower.contains("samsung") || lower.contains("tizen") || lower.contains("sec-rcr")) {
            return probe(host, 8001) ?: probe(host, 8002)
        }
        return null
    }

    override suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> = withContext(Dispatchers.IO) {
        val samsungKey = mapToSamsungKey(key)
            ?: return@withContext Result.failure(IllegalArgumentException("Unsupported key for Samsung: $key"))

        val wsUrl = buildWsUrl(device.formattedHost, device.port)
        val payload = buildKeyPayload(samsungKey)

        try {
            val deferred = CompletableDeferred<Unit>()
            val request = Request.Builder().url(wsUrl).build()

            val ws = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val sent = webSocket.send(payload)
                    if (sent) {
                        deferred.complete(Unit)
                    } else {
                        deferred.completeExceptionally(IOException("Failed to transmit frame"))
                    }
                    webSocket.close(1000, "Key dispatched")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(t)
                    }
                }
            })

            val result = withTimeoutOrNull(900) { deferred.await() }
            if (result != null) {
                Result.success(Unit)
            } else {
                ws.cancel()
                Result.failure(IOException("Samsung WebSocket transmission timeout"))
            }
        } catch (e: Exception) {
            SovereignLogger.w(SovereignLogger.Category.PROTOCOL, "Samsung sendKey($key) failed on ${device.ipAddress}: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val appId = when (appName.lowercase()) {
            "youtube" -> "111299001912"
            "netflix" -> "11101200001"
            "prime", "amazon" -> "3201512006785"
            else -> null
        }
        if (appId == null) {
            return@withContext Result.failure(IllegalArgumentException("Unknown Samsung App: $appName"))
        }

        val url = "http://${device.formattedHost}:${device.port}/api/v2/applications/$appId"
        try {
            val request = Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 201) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Samsung App Launch failed: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildWsUrl(host: String, port: Int): String {
        val appNameBase64 = Base64.encodeToString("ArgusRemote".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val scheme = if (port == 8002) "wss" else "ws"
        return "$scheme://$host:$port/api/v2/channels/samsung.remote.control?name=$appNameBase64"
    }

    private fun buildKeyPayload(keyName: String): String {
        val params = JSONObject()
        params.put("Cmd", "Click")
        params.put("DataOfCmd", keyName)
        params.put("Option", "false")
        params.put("TypeOfRemote", "SendRemoteKey")

        val root = JSONObject()
        root.put("method", "ms.remote.control")
        root.put("params", params)
        return root.toString()
    }

    private fun mapToSamsungKey(key: RemoteKey): String? = when (key) {
        RemoteKey.POWER -> "KEY_POWER"
        RemoteKey.HOME -> "KEY_HOME"
        RemoteKey.BACK -> "KEY_RETURN"
        RemoteKey.UP -> "KEY_UP"
        RemoteKey.DOWN -> "KEY_DOWN"
        RemoteKey.LEFT -> "KEY_LEFT"
        RemoteKey.RIGHT -> "KEY_RIGHT"
        RemoteKey.SELECT -> "KEY_ENTER"
        RemoteKey.VOLUME_UP -> "KEY_VOLUP"
        RemoteKey.VOLUME_DOWN -> "KEY_VOLDOWN"
        RemoteKey.MUTE -> "KEY_MUTE"
        RemoteKey.PLAY_PAUSE -> "KEY_PLAY"
        RemoteKey.REWIND -> "KEY_REWIND"
        RemoteKey.FAST_FORWARD -> "KEY_FF"
        RemoteKey.INFO -> "KEY_INFO"
    }
}
