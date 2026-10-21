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
import kotlinx.coroutines.*
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
    private val eventNodeCache = ConcurrentHashMap<String, String>()

    private fun resolveEventNode(ip: String, dadb: Dadb): String {
        return eventNodeCache.computeIfAbsent(ip) {
            try {
                val output = dadb.shell("getevent -S").allOutput
                var remoteNode: String? = null
                var kbdNode: String? = null
                val nodeRegex = Regex("""/dev/input/(event\d+)""")

                for (block in output.split("add device")) {
                    val nodeMatch = nodeRegex.find(block)?.groupValues?.get(1) ?: continue
                    val lower = block.lowercase()
                    if (lower.contains("amazon fire tv remote") || lower.contains("remote")) {
                        remoteNode = nodeMatch
                        break
                    } else if (lower.contains("keyboard") || lower.contains("amzkeyboard") || lower.contains("kbd")) {
                        kbdNode = nodeMatch
                    }
                }
                val resolved = remoteNode ?: kbdNode ?: "event4"
                Log.d("FireTvProtocol", "Resolved input event node for $ip: $resolved")
                resolved
            } catch (e: Exception) {
                Log.w("FireTvProtocol", "Could not resolve event node on $ip: ${e.message}")
                "event4"
            }
        }
    }

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

    private fun getDadb(ip: String, port: Int): Dadb =
        dadbCache.computeIfAbsent("$ip:$port") { Dadb.create(ip, port, keyPair) }

    private fun getOrCreateShellStream(ip: String, port: Int, dadb: Dadb): AdbShellStream? {
        val key = "$ip:$port"
        shellStreamCache[key]?.let { return it }
        return try {
            val stream = dadb.openShell("")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    while (isActive) stream.read()
                } catch (_: Throwable) {
                    shellStreamCache.remove(key)
                }
            }
            shellStreamCache[key] = stream
            stream
        } catch (e: Exception) {
            Log.w("FireTvProtocol", "Could not open persistent shell on $key: ${e.message}")
            null
        }
    }

    override suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> = withContext(Dispatchers.IO) {
        val (keycode, scancode) = when (key) {
            RemoteKey.POWER -> 26 to 116
            RemoteKey.HOME -> 3 to 172
            RemoteKey.BACK -> 4 to 158
            RemoteKey.UP -> 19 to 103
            RemoteKey.DOWN -> 20 to 108
            RemoteKey.LEFT -> 21 to 105
            RemoteKey.RIGHT -> 22 to 106
            RemoteKey.SELECT -> 23 to 96
            RemoteKey.VOLUME_UP -> 24 to 115
            RemoteKey.VOLUME_DOWN -> 25 to 114
            RemoteKey.MUTE -> 164 to 113
            RemoteKey.PLAY_PAUSE -> 85 to 164
            RemoteKey.REWIND -> 89 to 168
            RemoteKey.FAST_FORWARD -> 90 to 208
            RemoteKey.INFO -> 176 to 139 // Android KEYCODE_SETTINGS (Quick Settings / Options on Fire TV)
        }

        for (attempt in 1..2) {
            try {
                val dadb = getDadb(device.ipAddress, 5555)
                val eventNode = resolveEventNode(device.ipAddress, dadb)
                val fastCmd = if (key == RemoteKey.INFO) {
                    "input keyevent $keycode || (sendevent /dev/input/$eventNode 1 $scancode 1; sendevent /dev/input/$eventNode 0 0 0; sendevent /dev/input/$eventNode 1 $scancode 0; sendevent /dev/input/$eventNode 0 0 0)"
                } else {
                    "sendevent /dev/input/$eventNode 1 $scancode 1; sendevent /dev/input/$eventNode 0 0 0; sendevent /dev/input/$eventNode 1 $scancode 0; sendevent /dev/input/$eventNode 0 0 0 || input keyevent $keycode"
                }

                if (key == RemoteKey.POWER) {
                    var isAsleep = false
                    try {
                        val pwrState = dadb.shell("dumpsys power").allOutput
                        isAsleep = pwrState.contains("mWakefulness=Asleep") ||
                            pwrState.contains("Display Power: state=OFF") ||
                            pwrState.contains("mHoldingDisplaySuspendBlocker=false")
                    } catch (_: Exception) {}

                    if (isAsleep) {
                        val mac = device.macAddress ?: WakeOnLan.getMacFromArp(device.ipAddress)
                        if (!mac.isNullOrBlank()) {
                            WakeOnLan.wake(mac, device.ipAddress, context)
                        }
                        try { dadb.shell("input keyevent 224; sleep 0.1; input keyevent 3") } catch (_: Exception) {}
                        for (wakeAttempt in 1..4) {
                            delay(250)
                            try {
                                val checkOut = dadb.shell("dumpsys power").allOutput
                                if (checkOut.contains("mWakefulness=Awake") || checkOut.contains("Display Power: state=ON")) break
                            } catch (_: Exception) {}
                        }
                    } else {
                        dadb.shell(fastCmd)
                    }
                    return@withContext Result.success(Unit)
                }

                val stream = getOrCreateShellStream(device.ipAddress, 5555, dadb)
                if (stream != null) {
                    try {
                        synchronized(stream) { stream.write("$fastCmd\n") }
                        return@withContext Result.success(Unit)
                    } catch (e: Exception) {
                        shellStreamCache.remove("${device.ipAddress}:5555")?.let { try { it.close() } catch (_: Exception) {} }
                        dadbCache.remove("${device.ipAddress}:5555")
                        if (attempt == 1) continue
                    }
                }

                dadb.shell(fastCmd)
                return@withContext Result.success(Unit)
            } catch (e: Exception) {
                dadbCache.remove("${device.ipAddress}:5555")
                shellStreamCache.remove("${device.ipAddress}:5555")?.let { try { it.close() } catch (_: Exception) {} }
                if (attempt == 1) continue

                if (key == RemoteKey.POWER) {
                    val mac = device.macAddress ?: WakeOnLan.getMacFromArp(device.ipAddress)
                    if (!mac.isNullOrBlank()) WakeOnLan.wake(mac, device.ipAddress, context)
                }
                return@withContext Result.failure(e)
            }
        }
        Result.failure(Exception("Failed to send key $key to ${device.ipAddress} after self-healing retry"))
    }

    override fun resetConnections(ip: String?) {
        if (ip == null) {
            dadbCache.clear()
            shellStreamCache.values.forEach { try { it.close() } catch (_: Exception) {} }
            shellStreamCache.clear()
        } else {
            dadbCache.remove("$ip:5555")
            shellStreamCache.remove("$ip:5555")?.let { try { it.close() } catch (_: Exception) {} }
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
            Socket().use { s -> s.connect(InetSocketAddress(host, port), 250) }
            var model = "Amazon Fire TV"
            var name = "Fire TV Stick"
            val mac = WakeOnLan.getMacFromArp(host)

            try {
                withTimeout(400) {
                    val quickDadb = Dadb.create(host, port, keyPair, 400, 400)
                    try {
                        val rawModel = quickDadb.shell("getprop ro.product.model").allOutput.trim()
                        if (rawModel.isNotBlank()) {
                            model = when (rawModel) {
                                "AFTKA002", "AFTSSS" -> "Fire TV Stick 4K Max"
                                "AFTMM" -> "Fire TV Stick 4K"
                                "AFTT", "AFTKM" -> "Fire TV Stick"
                                "AFTB" -> "Fire TV Cube"
                                else -> "Fire TV ($rawModel)"
                            }
                            name = model
                        }
                    } finally {
                        try { quickDadb.close() } catch (_: Throwable) {}
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

    override suspend fun probeSsdp(host: String, port: Int, ssdpPayload: String): RemoteDevice? = probe(host, 5555)
}
