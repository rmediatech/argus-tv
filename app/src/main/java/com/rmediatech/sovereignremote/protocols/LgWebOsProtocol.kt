package com.rmediatech.sovereignremote.protocols

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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sovereign LG Smart TV (webOS) Protocol Engine:
 * Implements native webOS SSAP WebSocket protocol over port 3000 (ws) / 3001 (wss)
 * with zero cloud telemetry and zero external SDK dependencies.
 */
class LgWebOsProtocol(private val client: OkHttpClient? = null) : SmartTvProtocol {

    override val protocolId: String = "lg_webos"
    override val deviceType: DeviceType = DeviceType.LG_WEBOS
    override val defaultPort: Int = 3000

    override val signature = ProtocolSignature(
        protocolId = protocolId,
        deviceType = deviceType,
        ssdpSearchTargets = listOf(
            "urn:lge-com:service:webos-second-screen:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1"
        ),
        ssdpHeaderMatchers = listOf(
            "lge",
            "webos",
            ":3000",
            ":3001"
        ),
        candidatePorts = listOf(3000, 3001)
    )

    private val httpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(600, TimeUnit.MILLISECONDS)
        .writeTimeout(600, TimeUnit.MILLISECONDS)
        .readTimeout(600, TimeUnit.MILLISECONDS)
        .build()

    private val requestIdCounter = AtomicInteger(1)

    override suspend fun probe(host: String, port: Int): RemoteDevice? = withContext(Dispatchers.IO) {
        val targetPort = if (port == 3001) 3001 else 3000
        val isReachable = isTcpPortOpen(host, targetPort, 250)
        if (!isReachable) return@withContext null

        val mac = WakeOnLan.getMacFromArp(host)
        RemoteDevice(
            id = "lg_webos_$host",
            name = "LG webOS TV",
            ipAddress = host,
            port = targetPort,
            deviceType = DeviceType.LG_WEBOS,
            modelName = "LG Smart TV",
            isEthernet = false,
            macAddress = mac
        )
    }

    override suspend fun probeSsdp(host: String, port: Int, ssdpPayload: String): RemoteDevice? {
        val lower = ssdpPayload.lowercase()
        if (lower.contains("webos") || lower.contains("lge-com") || lower.contains("lg webos")) {
            return probe(host, 3000) ?: probe(host, 3001)
        }
        return null
    }

    override suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> = withContext(Dispatchers.IO) {
        val uri = mapToLgUri(key)
        val scheme = if (device.port == 3001) "wss" else "ws"
        val wsUrl = "$scheme://${device.formattedHost}:${device.port}/"

        try {
            val deferred = CompletableDeferred<Unit>()
            val request = Request.Builder().url(wsUrl).build()

            val ws = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val regPayload = buildRegisterPayload()
                    webSocket.send(regPayload)

                    val reqPayload = buildCommandPayload(uri)
                    val sent = webSocket.send(reqPayload)
                    if (sent) {
                        deferred.complete(Unit)
                    } else {
                        deferred.completeExceptionally(IOException("Failed to dispatch webOS command frame"))
                    }
                    webSocket.close(1000, "Command dispatched")
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
                Result.failure(IOException("LG webOS WebSocket transmission timeout"))
            }
        } catch (e: Exception) {
            SovereignLogger.w(SovereignLogger.Category.PROTOCOL, "LG sendKey($key) failed on ${device.ipAddress}: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val appId = when (appName.lowercase()) {
            "youtube" -> "youtube.leanback.v4"
            "netflix" -> "netflix"
            "prime", "amazon" -> "amazon"
            else -> appName
        }

        val scheme = if (device.port == 3001) "wss" else "ws"
        val wsUrl = "$scheme://${device.formattedHost}:${device.port}/"

        try {
            val deferred = CompletableDeferred<Unit>()
            val request = Request.Builder().url(wsUrl).build()

            val ws = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(buildRegisterPayload())

                    val launchReq = JSONObject()
                    launchReq.put("type", "request")
                    launchReq.put("id", "launch_${requestIdCounter.incrementAndGet()}")
                    launchReq.put("uri", "ssap://system.launcher/launch")
                    val params = JSONObject()
                    params.put("id", appId)
                    launchReq.put("payload", params)

                    webSocket.send(launchReq.toString())
                    deferred.complete(Unit)
                    webSocket.close(1000, "App launched")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!deferred.isCompleted) deferred.completeExceptionally(t)
                }
            })

            val res = withTimeoutOrNull(1000) { deferred.await() }
            if (res != null) Result.success(Unit) else {
                ws.cancel()
                Result.failure(IOException("LG webOS app launch timed out"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildRegisterPayload(): String {
        val root = JSONObject()
        root.put("type", "register")
        root.put("id", "reg_0")

        val payload = JSONObject()
        payload.put("forcePairing", false)
        payload.put("pairingType", "PROMPT")

        val manifest = JSONObject()
        manifest.put("manifestVersion", 1)
        manifest.put("appVersion", "1.0.0")

        val signed = JSONObject()
        signed.put("created", "20261015")
        signed.put("appId", "com.rmediatech.sovereignremote")
        signed.put("vendorId", "com.rmediatech")

        val permissions = JSONArray()
        permissions.put("CONTROL_AUDIO")
        permissions.put("CONTROL_POWER")
        permissions.put("READ_INSTALLED_APPS")
        permissions.put("CONTROL_DISPLAY")
        signed.put("permissions", permissions)

        manifest.put("signed", signed)
        payload.put("manifest", manifest)
        root.put("payload", payload)
        return root.toString()
    }

    private fun buildCommandPayload(uri: String): String {
        val root = JSONObject()
        root.put("type", "request")
        root.put("id", "req_${requestIdCounter.incrementAndGet()}")
        root.put("uri", uri)
        return root.toString()
    }

    private fun mapToLgUri(key: RemoteKey): String = when (key) {
        RemoteKey.VOLUME_UP -> "ssap://audio/volumeUp"
        RemoteKey.VOLUME_DOWN -> "ssap://audio/volumeDown"
        RemoteKey.MUTE -> "ssap://audio/setMute"
        RemoteKey.POWER -> "ssap://system/turnOff"
        RemoteKey.HOME -> "ssap://system.launcher/open"
        RemoteKey.PLAY_PAUSE -> "ssap://media.controls/play"
        RemoteKey.REWIND -> "ssap://media.controls/rewind"
        RemoteKey.FAST_FORWARD -> "ssap://media.controls/fastForward"
        RemoteKey.SELECT, RemoteKey.UP, RemoteKey.DOWN, RemoteKey.LEFT, RemoteKey.RIGHT -> "ssap://com.webos.service.ime/sendEnterKey"
        RemoteKey.BACK -> "ssap://tv/switchInput"
        RemoteKey.INFO -> "ssap://tv/getChannelInfo"
    }

    private fun isTcpPortOpen(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
