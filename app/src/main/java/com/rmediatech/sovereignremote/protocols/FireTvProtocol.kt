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
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class FireTvProtocol(private val context: Context) : SmartTvProtocol {
    override val deviceType: DeviceType = DeviceType.FIRE_TV
    override val defaultPort: Int = 5555

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
                Log.i("FireTvProtocol", "Seeded pre-authorized ADB keypair from assets")
            } catch (e: Exception) {
                Log.i("FireTvProtocol", "Generating fresh ADB keypair: ${e.message}")
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

    override suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> = withContext(Dispatchers.IO) {
        try {
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
                RemoteKey.INFO -> 82 // Menu key
            }

            val dadb = getDadb(device.ipAddress, device.port)
            dadb.shell("input keyevent $keycode")
            Log.d("FireTvProtocol", "Successfully sent key $key ($keycode) to ${device.ipAddress}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FireTvProtocol", "Error sending key $key to ${device.ipAddress}: ${e.message}", e)
            dadbCache.remove("${device.ipAddress}:${device.port}")
            Result.failure(e)
        }
    }

    override suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dadb = getDadb(device.ipAddress, device.port)
            val intent = when (appName.lowercase()) {
                "youtube" -> "com.amazon.firetv.youtube/com.google.android.apps.youtube.tv.activity.ShellActivity"
                "netflix" -> "com.netflix.ninja/.MainActivity"
                "prime" -> "com.amazon.avod/com.amazon.avod.client.activity.HomeScreenActivity"
                else -> appName
            }
            dadb.shell("am start -n $intent")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun probe(ip: String, port: Int): RemoteDevice? = withContext(Dispatchers.IO) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), 400)
            }
            var model = "Amazon Fire OS"
            var name = "Fire TV Stick"
            try {
                val dadb = getDadb(ip, port)
                val rawModel = dadb.shell("getprop ro.product.model").allOutput.trim()
                if (rawModel.isNotBlank()) {
                    model = when (rawModel) {
                        "AFTKA002" -> "Fire TV Stick 4K Max"
                        "AFTMM" -> "Fire TV Stick 4K"
                        "AFTSSS" -> "Fire TV Stick 4K Max"
                        "AFTT", "AFTKM" -> "Fire TV Stick"
                        "AFTB" -> "Fire TV Cube"
                        else -> "Fire TV ($rawModel)"
                    }
                    name = model
                }
            } catch (_: Exception) {}

            RemoteDevice(
                id = "firetv_$ip",
                name = name,
                ipAddress = ip,
                port = port,
                deviceType = DeviceType.FIRE_TV,
                modelName = model,
                isEthernet = false
            )
        } catch (_: Exception) {
            null
        }
    }
}
