package com.rmediatech.sovereignremote.protocols

import android.content.Context
import android.util.Log
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GoogleTvProtocol(private val context: Context? = null) : SmartTvProtocol {
    override val protocolId: String = "googletv"
    override val deviceType: DeviceType = DeviceType.GOOGLE_TV
    override val defaultPort: Int = 8008

    override val signature = ProtocolSignature(
        protocolId = protocolId,
        deviceType = deviceType,
        ssdpSearchTargets = listOf("urn:dial-multiscreen-org:service:dial:1", "ssdp:all"),
        ssdpHeaderMatchers = listOf("eureka", "google", "chromecast", "googletv"),
        candidatePorts = listOf(8008, 5555)
    )

    private val dadbCache = ConcurrentHashMap<String, Dadb>()

    private val keyPair: AdbKeyPair by lazy {
        val keyDir = context?.filesDir?.let { File(it, "adb") } ?: File(System.getProperty("java.io.tmpdir", "/tmp"), "adb")
        keyDir.mkdirs()
        val privKey = File(keyDir, "adbkey")
        val pubKey = File(keyDir, "adbkey.pub")
        if (!privKey.exists() || !pubKey.exists()) {
            AdbKeyPair.generate(privKey, pubKey)
        }
        AdbKeyPair.read(privKey, pubKey)
    }

    private fun getDadb(ip: String, port: Int): Dadb {
        return dadbCache.computeIfAbsent("$ip:$port") {
            Dadb.create(ip, port, keyPair)
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .writeTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(500, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> = withContext(Dispatchers.IO) {
        if (device.deviceType == DeviceType.FIRE_TV || device.name.contains("Fire", ignoreCase = true)) {
            return@withContext Result.failure(IllegalStateException("Device is Amazon Fire TV"))
        }

        val keycode = when (key) {
            RemoteKey.POWER -> 26
            RemoteKey.HOME -> 3
            RemoteKey.BACK -> 4
            RemoteKey.UP -> 19
            RemoteKey.DOWN -> 20
            RemoteKey.LEFT -> 21
            RemoteKey.RIGHT -> 22
            RemoteKey.SELECT -> 23
            RemoteKey.VOLUME_UP -> 24
            RemoteKey.VOLUME_DOWN -> 25
            RemoteKey.MUTE -> 164
            RemoteKey.PLAY_PAUSE -> 85
            RemoteKey.REWIND -> 89
            RemoteKey.FAST_FORWARD -> 90
            RemoteKey.INFO -> 176 // Android KEYCODE_SETTINGS (Quick Settings / Options on Google TV)
        }

        // 1. Primary: Direct ADB control via port 5555
        if (isPortOpen(device.ipAddress, 5555, 300)) {
            try {
                val dadb = getDadb(device.ipAddress, 5555)

                if (key == RemoteKey.POWER) {
                    val pwrState = dadb.shell("dumpsys power").allOutput
                    val isAsleep = pwrState.contains("mWakefulness=Asleep") ||
                        pwrState.contains("Display Power: state=OFF") ||
                        pwrState.contains("mHoldingDisplaySuspendBlocker=false")

                    dadb.shell("input keyevent $keycode")
                    Log.d("GoogleTvProtocol", "Sent ADB POWER keyevent $keycode to ${device.ipAddress}")

                    if (isAsleep) {
                        val mac = device.macAddress ?: com.rmediatech.sovereignremote.net.WakeOnLan.getMacFromArp(device.ipAddress)
                        if (!mac.isNullOrBlank()) {
                            Log.i("GoogleTvProtocol", "TV ${device.ipAddress} is sleeping. Broadcasting WoL to MAC $mac...")
                            com.rmediatech.sovereignremote.net.WakeOnLan.wake(mac, device.ipAddress, context)
                        }
                        for (attempt in 1..4) {
                            delay(250)
                            try {
                                dadb.shell("input keyevent 224; sleep 0.1; input keyevent 3")
                                val checkOut = dadb.shell("dumpsys power").allOutput
                                if (checkOut.contains("mWakefulness=Awake") || checkOut.contains("Display Power: state=ON")) {
                                    Log.i("GoogleTvProtocol", "TV screen awakened via ADB KEYCODE_WAKEUP on attempt $attempt")
                                    break
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    dadb.shell("input keyevent $keycode")
                    Log.d("GoogleTvProtocol", "Sent ADB keyevent $keycode to ${device.ipAddress}")
                }
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

        Result.failure(IOException("TV is offline or unreachable on ADB"))
    }

    override suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "http://${device.formattedHost}:8008/apps/$appName"
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

    override suspend fun probe(host: String, port: Int): RemoteDevice? = withContext(Dispatchers.IO) {
        try {
            val formattedHost = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
            val url = "http://$formattedHost:8008/setup/eureka_info"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val rawName = json.optString("name").ifBlank { json.optString("device_name", "") }
                        .replace("&#39;", "'").replace("&amp;", "&").replace("&quot;", "\"")
                    val bssid = json.optString("bssid").ifBlank { json.optString("hotspot_bssid").ifBlank { host } }
                    val isEthernet = json.optBoolean("ethernet_connected", false)

                    // Dynamic manufacturer & model discovery
                    val deviceInfo = json.optJSONObject("device_info")
                    val manufacturer = deviceInfo?.optString("manufacturer").orEmpty().ifBlank {
                        json.optString("manufacturer", "")
                    }
                    val modelNameFromEureka = deviceInfo?.optString("model_name").orEmpty().ifBlank {
                        json.optString("model_name", "")
                    }

                    val displayName = when {
                        rawName.isNotBlank() -> rawName
                        modelNameFromEureka.isNotBlank() -> "$modelNameFromEureka ($host)"
                        else -> "Google TV ($host)"
                    }

                    val resolvedModel = when {
                        manufacturer.isNotBlank() && modelNameFromEureka.isNotBlank() -> "$manufacturer $modelNameFromEureka"
                        modelNameFromEureka.isNotBlank() -> modelNameFromEureka
                        manufacturer.isNotBlank() -> "$manufacturer Google TV"
                        displayName.contains("TCL", ignoreCase = true) -> "TCL Google TV"
                        displayName.contains("Hisense", ignoreCase = true) -> "Hisense Google TV"
                        displayName.contains("Sony", ignoreCase = true) -> "Sony BRAVIA"
                        displayName.contains("Chromecast", ignoreCase = true) -> "Chromecast"
                        displayName.contains("NVIDIA", ignoreCase = true) || displayName.contains("Shield", ignoreCase = true) -> "NVIDIA SHIELD"
                        else -> "Google / Android TV"
                    }

                    // Extract MAC address from ARP, BSSID (if standard MAC format), or active ADB interface
                    var mac = com.rmediatech.sovereignremote.net.WakeOnLan.getMacFromArp(host)
                    if (mac.isNullOrBlank() && bssid.matches(Regex("""([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}"""))) {
                        mac = bssid.lowercase()
                    }
                    if (mac.isNullOrBlank() && isPortOpen(host, 5555, 200)) {
                        mac = extractMacFromAdb(host, 5555)
                    }

                    RemoteDevice(
                        id = "googletv_$bssid",
                        name = displayName,
                        ipAddress = host,
                        port = 8008,
                        deviceType = DeviceType.GOOGLE_TV,
                        modelName = resolvedModel,
                        isEthernet = isEthernet,
                        macAddress = mac
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun probeSsdp(host: String, port: Int, ssdpPayload: String): RemoteDevice? {
        return probe(host, 8008)
    }

    private fun extractMacFromAdb(ip: String, port: Int): String? {
        return try {
            val dadb = getDadb(ip, port)
            val output = dadb.shell("ip link").allOutput
            val match = Regex("""(?:eth0|wlan0)[^\n]*\n\s*link/ether\s+([0-9a-fA-F:]{17})""").find(output)
            match?.groupValues?.get(1)?.lowercase()
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
