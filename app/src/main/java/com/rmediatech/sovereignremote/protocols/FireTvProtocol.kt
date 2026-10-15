package com.rmediatech.sovereignremote.protocols

import android.content.Context
import android.util.Log
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import dadb.AdbKeyPair
import dadb.AdbShellStream
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class FireTvProtocol(private val context: Context? = null) : SmartTvProtocol {
    override val protocolId: String = "firetv"
    override val deviceType: DeviceType = DeviceType.FIRE_TV
    override val defaultPort: Int = 5555

    override val signature = ProtocolSignature(
        protocolId = protocolId,
        deviceType = deviceType,
        ssdpSearchTargets = listOf("urn:amazon:device:amzn-device:1", "ssdp:all"),
        ssdpHeaderMatchers = listOf("amzn", "firetv", "amazon"),
        candidatePorts = listOf(5555)
    )

    private val dadbCache = ConcurrentHashMap<String, Dadb>()
    private val shellStreamCache = ConcurrentHashMap<String, AdbShellStream>()

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
            Dadb.create(ip, port, keyPair, 1500, 1500)
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
        if (existing != null) {
            return existing
        }

        return try {
            val newStream = dadb.openShell()
            shellStreamCache[key] = newStream
            newStream
        } catch (e: Exception) {
            Log.w("FireTvProtocol", "Could not open persistent shell on $key: ${e.message}")
            null
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
            RemoteKey.INFO -> 165
        }

        try {
            val dadb = getDadb(device.ipAddress, device.port)

            if (key == RemoteKey.POWER) {
                var isAsleep = false
                try {
                    val pwrState = dadb.shell("dumpsys power").allOutput
                    isAsleep = pwrState.contains("mWakefulness=Asleep") ||
                        pwrState.contains("Display Power: state=OFF") ||
                        pwrState.contains("mHoldingDisplaySuspendBlocker=false")
                } catch (_: Exception) {}

                if (isAsleep) {
                    val mac = device.macAddress ?: com.rmediatech.sovereignremote.net.WakeOnLan.getMacFromArp(device.ipAddress)
                    if (!mac.isNullOrBlank()) {
                        Log.i("FireTvProtocol", "Fire TV ${device.ipAddress} is sleeping. Broadcasting instant WoL to $mac...")
                        com.rmediatech.sovereignremote.net.WakeOnLan.wake(mac, device.ipAddress, context)
                    }

                    try {
                        dadb.shell("input keyevent 224; sleep 0.1; input keyevent 3")
                    } catch (_: Exception) {}

                    for (attempt in 1..4) {
                        delay(250)
                        try {
                            val checkOut = dadb.shell("dumpsys power").allOutput
                            if (checkOut.contains("mWakefulness=Awake") || checkOut.contains("Display Power: state=ON")) {
                                Log.i("FireTvProtocol", "Fire TV display awakened via KEYCODE_WAKEUP on attempt $attempt")
                                break
                            }
                        } catch (_: Exception) {}
                    }
                } else {
                    dadb.shell("input keyevent $keycode")
                }
                return@withContext Result.success(Unit)
            }

            // High-throughput fast path via persistent shell stream
            val stream = getOrCreateShellStream(device.ipAddress, device.port, dadb)
            if (stream != null) {
                try {
                    stream.write("input keyevent $keycode\n")
                    Log.d("FireTvProtocol", "Piped key $key ($keycode) to ${device.ipAddress}")
                    return@withContext Result.success(Unit)
                } catch (e: Exception) {
                    Log.w("FireTvProtocol", "Piped shell write failed, resetting stream: ${e.message}")
                    shellStreamCache.remove("${device.ipAddress}:${device.port}")
                    try { stream.close() } catch (_: Exception) {}
                }
            }

            // Fallback: standard shell execution
            dadb.shell("input keyevent $keycode")
            Log.d("FireTvProtocol", "Successfully sent key $key ($keycode) to ${device.ipAddress}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FireTvProtocol", "ADB command failed on ${device.ipAddress}: ${e.message}")
            dadbCache.remove("${device.ipAddress}:${device.port}")
            shellStreamCache.remove("${device.ipAddress}:${device.port}")

            if (key == RemoteKey.POWER) {
                val mac = device.macAddress ?: com.rmediatech.sovereignremote.net.WakeOnLan.getMacFromArp(device.ipAddress)
                if (!mac.isNullOrBlank()) {
                    Log.i("FireTvProtocol", "Fire TV ${device.ipAddress} fallback: Broadcasting WoL to $mac...")
                    com.rmediatech.sovereignremote.net.WakeOnLan.wake(mac, device.ipAddress, context)
                }
            }

            Result.failure(e)
        }
    }

    override suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dadb = getDadb(device.ipAddress, device.port)
            val intent = when (appName.lowercase()) {
                "netflix" -> "com.netflix.ninja/.MainActivity"
                "youtube" -> "com.amazon.firetv.youtube/com.google.android.apps.youtube.tv.activity.ShellActivity"
                "prime" -> "com.amazon.avod/com.amazon.avod.client.activity.HomeScreenActivity"
                else -> appName
            }
            dadb.shell("am start -n $intent")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun probe(host: String, port: Int): RemoteDevice? = withContext(Dispatchers.IO) {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), 250)
            }
            var model = "Amazon Fire TV"
            var name = "Fire TV Stick"
            val mac = com.rmediatech.sovereignremote.net.WakeOnLan.getMacFromArp(host)

            try {
                withTimeout(400) {
                    val quickDadb = Dadb.create(host, port, keyPair, 400, 400)
                    val rawModel = quickDadb.shell("getprop ro.product.model").allOutput.trim()
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
                }
            } catch (_: Throwable) {}

            RemoteDevice(
                id = "firetv_$host",
                name = name,
                ipAddress = host,
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

    override suspend fun probeSsdp(host: String, port: Int, ssdpPayload: String): RemoteDevice? {
        return probe(host, 5555)
    }
}
