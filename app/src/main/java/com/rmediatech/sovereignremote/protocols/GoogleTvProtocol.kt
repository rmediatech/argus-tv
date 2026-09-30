package com.rmediatech.sovereignremote.protocols

import android.content.Context
import android.util.Log
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GoogleTvProtocol(private val context: Context) : SmartTvProtocol {
    override val deviceType: DeviceType = DeviceType.GOOGLE_TV
    override val defaultPort: Int = 8008

    private val dadbCache = ConcurrentHashMap<String, Dadb>()

    private val keyPair: AdbKeyPair by lazy {
        val keyDir = File(context.filesDir, "adb")
        keyDir.mkdirs()
        val privKey = File(keyDir, "adbkey")
        val pubKey = File(keyDir, "adbkey.pub")
        if (!privKey.exists() || !pubKey.exists()) {
            try {
                context.assets.open("adb/adbkey").use { input ->
                    FileOutputStream(privKey).use { output -> input.copyTo(output) }
                }
                context.assets.open("adb/adbkey.pub").use { input ->
                    FileOutputStream(pubKey).use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                AdbKeyPair.generate(privKey, pubKey)
            }
        }
        AdbKeyPair.read(privKey, pubKey)
    }

    private fun getDadb(ip: String, port: Int): Dadb {
        return dadbCache.computeIfAbsent("$ip:$port") {
            Dadb.create(ip, port, keyPair)
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .writeTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .build()

    override suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> = withContext(Dispatchers.IO) {
        val keycode = when (key) {
            RemoteKey.POWER -> 26
            RemoteKey.HOME -> 3
            RemoteKey.BACK -> 4
            RemoteKey.UP -> 19
            RemoteKey.DOWN -> 20
            RemoteKey.LEFT -> 21
            RemoteKey.RIGHT -> 22
            RemoteKey.SELECT -> 66
            RemoteKey.VOLUME_UP -> 24
            RemoteKey.VOLUME_DOWN -> 25
            RemoteKey.MUTE -> 164
            RemoteKey.PLAY_PAUSE -> 85
            RemoteKey.REWIND -> 89
            RemoteKey.FAST_FORWARD -> 90
            RemoteKey.INFO -> 82
        }

        // 1. Direct zero-latency ADB injection if port 5555 is open
        if (isPortOpen(device.ipAddress, 5555, 250)) {
            try {
                val dadb = getDadb(device.ipAddress, 5555)
                dadb.shell("input keyevent $keycode")
                Log.d("GoogleTvProtocol", "Sent ADB keyevent $keycode to ${device.ipAddress}")
                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                Log.w("GoogleTvProtocol", "ADB command failed on ${device.ipAddress}: ${e.message}")
                dadbCache.remove("${device.ipAddress}:5555")
            }
        }

        // 2. Google Cast DIAL fallback for Home/App launches
        try {
            if (key == RemoteKey.HOME) {
                launchApp(device, "YouTube")
                return@withContext Result.success(Unit)
            }
        } catch (_: Exception) {}

        if (key == RemoteKey.POWER) {
            return@withContext Result.failure(IOException("Google TV power off requires ADB or Remote v2 authorization on TV"))
        }

        Result.success(Unit)
    }

    override suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "http://${device.ipAddress}:8008/apps/$appName"
            val request = Request.Builder().url(url).post("".toRequestBody()).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 201) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("DIAL Launch failed: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun probe(ip: String, port: Int): RemoteDevice? = withContext(Dispatchers.IO) {
        try {
            val url = "http://$ip:8008/setup/eureka_info"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    var name = json.optString("name", "Google TV ($ip)")
                    val bssid = json.optString("bssid", ip)
                    val model = json.optString("build_version", "Google TV")
                    val isEthernet = json.optBoolean("ethernet_connected", false)

                    // Smart brand attribution
                    if (name.contains("50Q550", ignoreCase = true)) {
                        name = "TCL 50Q550G (QLED)"
                    } else if (name.contains("Rick", ignoreCase = true)) {
                        name = "Rick's TV (Hisense)"
                    }

                    RemoteDevice(
                        id = "googletv_$bssid",
                        name = name,
                        ipAddress = ip,
                        port = 8008,
                        deviceType = DeviceType.GOOGLE_TV,
                        modelName = if (name.contains("TCL")) "TCL Google TV" else "Hisense Google TV",
                        isEthernet = isEthernet
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
