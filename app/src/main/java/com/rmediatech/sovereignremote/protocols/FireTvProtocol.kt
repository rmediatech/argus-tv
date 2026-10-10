package com.rmediatech.sovereignremote.protocols

import android.content.Context
import android.util.Log
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.net.WakeOnLan
import dadb.AdbKeyPair
import dadb.AdbShellStream
import dadb.Dadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class FireTvProtocol(private val context: Context) : SmartTvProtocol {
    override val deviceType: DeviceType = DeviceType.FIRE_TV
    override val defaultPort: Int = 5555

    private val dadbCache = ConcurrentHashMap<String, Dadb>()
    private val shellStreamCache = ConcurrentHashMap<String, AdbShellStream>()

    private val keyPair: AdbKeyPair by lazy {
        val keyDir = File(context.filesDir, "adb")
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

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrCreateShellStream(ip: String, port: Int, dadb: Dadb): AdbShellStream? {
        val key = "$ip:$port"
        val existing = shellStreamCache[key]
        if (existing != null) return existing
        return try {
            val stream = dadb.openShell("")
            shellStreamCache[key] = stream
            stream
        } catch (e: Exception) {
            Log.w("FireTvProtocol", "Could not open persistent shell on $key: ${e.message}")
            null
        }
    }

    private fun invalidateCaches(ip: String, port: Int) {
        val key = "$ip:$port"
        shellStreamCache.remove(key)?.let { stream ->
            try { stream.close() } catch (_: Exception) {}
        }
        dadbCache.remove(key)?.let { dadbInstance ->
            try { dadbInstance.close() } catch (_: Exception) {}
        }
    }

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
            RemoteKey.INFO -> 82 // Menu key
        }

        // Fast-path for POWER / WAKE key:
        if (key == RemoteKey.POWER) {
            val isAwake = isPortOpen(device.ipAddress, device.port, 150)
            if (!isAwake) {
                // TV is sleeping / port 5555 closed. Blast instant WoL without waiting for Dadb TCP timeout!
                val mac = device.macAddress ?: WakeOnLan.getMacFromArp(device.ipAddress)
                if (!mac.isNullOrBlank()) {
                    Log.i("FireTvProtocol", "Fire TV ${device.ipAddress} is sleeping. Broadcasting instant WoL to $mac...")
                    WakeOnLan.wake(mac, device.ipAddress, context)

                    // Fast background watcher: when port 5555 opens, send KEYCODE_WAKEUP (224) to turn on display
                    CoroutineScope(Dispatchers.IO).launch {
                        for (attempt in 1..15) {
                            delay(250)
                            if (isPortOpen(device.ipAddress, device.port, 150)) {
                                try {
                                    val dadb = getDadb(device.ipAddress, device.port)
                                    dadb.shell("input keyevent 224") // WAKEUP
                                    Log.i("FireTvProtocol", "Fire TV display awakened via KEYCODE_WAKEUP on attempt $attempt")
                                    break
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    return@withContext Result.success(Unit)
                }
                return@withContext Result.failure(
                    IOException("Cannot wake Fire TV: MAC address unknown. Please turn TV on once to discover its MAC.")
                )
            }
        }

        // TV is awake or regular navigation key: try fast piped stream (<5ms), fall back to dadb.shell
        try {
            val dadb = getDadb(device.ipAddress, device.port)
            val stream = getOrCreateShellStream(device.ipAddress, device.port, dadb)
            if (stream != null) {
                try {
                    synchronized(stream) {
                        stream.write("input keyevent $keycode\n")
                    }
                    Log.d("FireTvProtocol", "Piped key $key ($keycode) to ${device.ipAddress}")
                    return@withContext Result.success(Unit)
                } catch (e: Exception) {
                    Log.w("FireTvProtocol", "Piped shell write failed, resetting stream: ${e.message}")
                    shellStreamCache.remove("${device.ipAddress}:${device.port}")?.let {
                        try { it.close() } catch (_: Exception) {}
                    }
                }
            }

            // Fallback to direct shell command
            dadb.shell("input keyevent $keycode")
            Log.d("FireTvProtocol", "Successfully sent key $key ($keycode) to ${device.ipAddress}")
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FireTvProtocol", "ADB command failed on ${device.ipAddress}: ${e.message}")
            invalidateCaches(device.ipAddress, device.port)

            // If POWER key was pressed and direct ADB failed, fallback to WoL
            if (key == RemoteKey.POWER) {
                val mac = device.macAddress ?: WakeOnLan.getMacFromArp(device.ipAddress)
                if (!mac.isNullOrBlank()) {
                    Log.i("FireTvProtocol", "Fire TV ${device.ipAddress} fallback: Broadcasting WoL to $mac...")
                    WakeOnLan.wake(mac, device.ipAddress, context)
                    return@withContext Result.success(Unit)
                }
            }
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
            var mac = com.rmediatech.sovereignremote.net.WakeOnLan.getMacFromArp(ip)

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

                if (mac.isNullOrBlank()) {
                    val linkOut = dadb.shell("ip link").allOutput
                    val wlanMatch = Regex("""\bwlan0\b[^\n]*\n\s*link/ether\s+([0-9a-fA-F:]{17})""").find(linkOut)
                    val ethMatch = Regex("""\beth0\b[^\n]*\n\s*link/ether\s+([0-9a-fA-F:]{17})""").find(linkOut)
                    mac = wlanMatch?.groupValues?.get(1)?.lowercase() ?: ethMatch?.groupValues?.get(1)?.lowercase()
                }
            } catch (_: Exception) {}

            RemoteDevice(
                id = "firetv_$ip",
                name = name,
                ipAddress = ip,
                port = port,
                deviceType = DeviceType.FIRE_TV,
                modelName = model,
                isEthernet = false,
                macAddress = mac
            )
        } catch (_: Exception) {
            null
        }
    }
}
