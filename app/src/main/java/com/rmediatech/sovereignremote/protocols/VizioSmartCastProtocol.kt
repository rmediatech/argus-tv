package com.rmediatech.sovereignremote.protocols

import android.content.Context
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class VizioPairingChallenge(
    val pairingReqToken: Long,
    val challengeType: Int = 1
)

/**
 * Sovereign Vizio SmartCast Protocol Engine:
 * Implements native Vizio HTTPS REST remote protocol over port 7345 / 9000
 * with on-screen 4-digit PIN pairing, zero cloud telemetry, and zero proprietary vendor SDKs.
 */
class VizioSmartCastProtocol(
    private val context: Context? = null,
    private val client: OkHttpClient? = null
) : SmartTvProtocol {

    override val protocolId: String = "vizio_smartcast"
    override val deviceType: DeviceType = DeviceType.VIZIO_SMARTCAST
    override val defaultPort: Int = 7345

    override val signature = ProtocolSignature(
        protocolId = protocolId,
        deviceType = deviceType,
        ssdpSearchTargets = listOf(
            "urn:schemas-vizio-com:device:smartcast:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1"
        ),
        ssdpHeaderMatchers = listOf(
            "vizio",
            "smartcast",
            ":7345",
            ":9000"
        ),
        candidatePorts = listOf(7345, 9000)
    )

    private val authTokenCache = ConcurrentHashMap<String, String>()

    private val httpClient: OkHttpClient by lazy {
        client ?: buildTolerantClient()
    }

    fun getAuthToken(host: String): String? {
        authTokenCache[host]?.let { return it }
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences("sovereign_remote", Context.MODE_PRIVATE)
                val stored = prefs.getString("vizio_auth_$host", null)
                if (!stored.isNullOrBlank()) {
                    authTokenCache[host] = stored
                    return stored
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun setAuthToken(host: String, token: String) {
        authTokenCache[host] = token
        context?.let { ctx ->
            try {
                ctx.getSharedPreferences("sovereign_remote", Context.MODE_PRIVATE)
                    .edit()
                    .putString("vizio_auth_$host", token)
                    .apply()
            } catch (_: Exception) {}
        }
    }

    fun hasPairingToken(host: String): Boolean = !getAuthToken(host).isNullOrBlank()

    suspend fun startPairing(host: String, port: Int = 7345): Result<VizioPairingChallenge> = withContext(Dispatchers.IO) {
        try {
            val url = "https://$host:$port/pairing/start"
            val json = JSONObject().apply {
                put("DEVICE_ID", "argus_controller")
                put("DEVICE_NAME", "Argus Remote")
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(url).put(body).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val respJson = JSONObject(resp.body?.string().orEmpty())
                    val item = respJson.optJSONObject("ITEM")
                    val token = item?.optLong("PAIRING_REQ_TOKEN") ?: 0L
                    val challenge = item?.optInt("CHALLENGE_TYPE", 1) ?: 1
                    Result.success(VizioPairingChallenge(token, challenge))
                } else {
                    Result.failure(IOException("Pairing start failed: HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun submitPin(
        host: String,
        port: Int = 7345,
        challenge: VizioPairingChallenge,
        pin: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://$host:$port/pairing/pair"
            val json = JSONObject().apply {
                put("DEVICE_ID", "argus_controller")
                put("CHALLENGE_TYPE", challenge.challengeType)
                put("RESPONSE_VALUE", pin.trim())
                put("PAIRING_REQ_TOKEN", challenge.pairingReqToken)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(url).put(body).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val respJson = JSONObject(resp.body?.string().orEmpty())
                    val item = respJson.optJSONObject("ITEM")
                    val authToken = item?.optString("AUTH_TOKEN")
                    if (!authToken.isNullOrBlank()) {
                        setAuthToken(host, authToken)
                        Result.success(authToken)
                    } else {
                        Result.failure(IOException("Invalid PIN or empty token received"))
                    }
                } else {
                    Result.failure(IOException("PIN verification failed: HTTP ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun probe(host: String, port: Int): RemoteDevice? = withContext(Dispatchers.IO) {
        val targetPort = if (port == 9000) 9000 else 7345
        if (!isTcpPortOpen(host, targetPort, 200)) return@withContext null

        val mac = WakeOnLan.getMacFromArp(host)
        RemoteDevice(
            id = "vizio_$host",
            name = "Vizio SmartCast TV",
            ipAddress = host,
            port = targetPort,
            deviceType = DeviceType.VIZIO_SMARTCAST,
            modelName = "Vizio SmartCast Display",
            isEthernet = false,
            macAddress = mac
        )
    }

    override suspend fun probeSsdp(host: String, port: Int, ssdpPayload: String): RemoteDevice? {
        val lower = ssdpPayload.lowercase()
        if (lower.contains("vizio") || lower.contains("smartcast")) {
            return probe(host, 7345) ?: probe(host, 9000)
        }
        return null
    }

    override suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> = withContext(Dispatchers.IO) {
        val (codeset, code) = mapToVizioCode(key)
        val url = "https://${device.formattedHost}:${device.port}/key_command/"

        val keyItem = JSONObject()
        keyItem.put("CODESET", codeset)
        keyItem.put("CODE", code)
        keyItem.put("ACTION", "KEYPRESS")

        val keyList = JSONArray()
        keyList.put(keyItem)

        val root = JSONObject()
        root.put("KEYLIST", keyList)

        try {
            val body = root.toString().toRequestBody("application/json".toMediaType())
            val reqBuilder = Request.Builder().url(url).put(body)

            val token = getAuthToken(device.ipAddress)
            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Auth", token)
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful || response.code == 200 || response.code == 204) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Vizio Key Error: HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            SovereignLogger.w(SovereignLogger.Category.PROTOCOL, "Vizio sendKey($key) failed on ${device.ipAddress}: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> = withContext(Dispatchers.IO) {
        Result.success(Unit)
    }

    private fun mapToVizioCode(key: RemoteKey): Pair<Int, Int> = when (key) {
        RemoteKey.POWER -> Pair(11, 0)
        RemoteKey.HOME -> Pair(4, 15)
        RemoteKey.BACK -> Pair(4, 0)
        RemoteKey.UP -> Pair(3, 8)
        RemoteKey.DOWN -> Pair(3, 3)
        RemoteKey.LEFT -> Pair(3, 1)
        RemoteKey.RIGHT -> Pair(3, 7)
        RemoteKey.SELECT -> Pair(3, 2)
        RemoteKey.VOLUME_UP -> Pair(5, 0)
        RemoteKey.VOLUME_DOWN -> Pair(5, 1)
        RemoteKey.MUTE -> Pair(5, 2)
        RemoteKey.PLAY_PAUSE -> Pair(2, 2)
        RemoteKey.REWIND -> Pair(2, 0)
        RemoteKey.FAST_FORWARD -> Pair(2, 1)
        RemoteKey.INFO -> Pair(4, 13)
    }

    private fun buildTolerantClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(600, TimeUnit.MILLISECONDS)
            .writeTimeout(600, TimeUnit.MILLISECONDS)
            .readTimeout(600, TimeUnit.MILLISECONDS)
            .build()
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
