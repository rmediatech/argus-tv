package com.rmediatech.sovereignremote.crucible

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Sovereign Universal TV Simulator (Digital Twin):
 * One Job: Spawns ephemeral, bare-metal network servers that emulate real-world Smart TV protocols
 * (Roku ECP REST, Samsung Tizen REST/WS, LG webOS WebSocket, and Android/Fire TV ADB shell).
 * Implements strict AutoCloseable RAII semantics: spins up on an ephemeral port, captures incoming commands,
 * streams simulated hardware state/telemetry, and tears down completely without leaving socket or memory residue.
 */
class SovereignTvTwin(
    val brand: TvBrand,
    val modelName: String = "Sovereign Virtual TV",
    val personality: TvPersonality = TvPersonality.StandardCompliant
) : AutoCloseable {

    enum class TvBrand {
        ROKU,
        FIRE_TV,
        GOOGLE_TV,
        SAMSUNG_TIZEN,
        LG_WEBOS
    }

    sealed class TvPersonality {
        data object StandardCompliant : TvPersonality()
        data class ProfilePickerInterception(val delayBeforeShowingPickerMs: Long = 100L) : TvPersonality()
        data class BufferingLag(val lagMs: Long) : TvPersonality()
        data class PacketDropper(val dropKeyRatio: Float = 0.5f) : TvPersonality()
    }

    private val isRunning = AtomicBoolean(true)
    private var serverSocket: ServerSocket? = null
    private var workerThread: Thread? = null

    val receivedKeypresses = CopyOnWriteArrayList<String>()
    val receivedLaunches = CopyOnWriteArrayList<String>()
    val receivedShellCommands = CopyOnWriteArrayList<String>()

    var activeApp: String = "tv.empty"
    var playbackStatus: String = "idle"
    var playheadMs: Long = 0L

    val port: Int
        get() = serverSocket?.localPort ?: throw IllegalStateException("TvTwin is not bound")

    init {
        val s = ServerSocket(0) // Ephemeral OS-assigned port
        serverSocket = s
        workerThread = thread(name = "TvTwin-${brand.name}-${s.localPort}", isDaemon = true) {
            try {
                while (isRunning.get() && !s.isClosed) {
                    val client = try {
                        s.accept()
                    } catch (_: Exception) {
                        break
                    }
                    handleClient(client)
                }
            } finally {
                s.close()
            }
        }
    }

    private fun handleClient(client: Socket) {
        thread(name = "TvTwinClient-${client.port}", isDaemon = true) {
            try {
                client.use { sock ->
                    when (brand) {
                        TvBrand.ROKU -> handleRokuEcp(sock)
                        TvBrand.SAMSUNG_TIZEN -> handleSamsungRest(sock)
                        TvBrand.LG_WEBOS -> handleLgWebOs(sock)
                        TvBrand.FIRE_TV, TvBrand.GOOGLE_TV -> handleAdbWire(sock)
                    }
                }
            } catch (_: Exception) {
                // Client disconnected or test ended
            }
        }
    }

    private fun handleRokuEcp(sock: Socket) {
        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
        val line = reader.readLine() ?: return
        val parts = line.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val path = parts[1]

        val writer = OutputStreamWriter(sock.getOutputStream())

        when {
            path.startsWith("/keypress/") -> {
                val key = path.substringAfter("/keypress/")
                receivedKeypresses.add(key)
                if (key == "Play" || key == "Pause") {
                    playbackStatus = if (playbackStatus == "play") "pause" else "play"
                }
                writer.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                writer.flush()
            }
            path.startsWith("/launch/") -> {
                val app = path.substringAfter("/launch/").substringBefore("?")
                receivedLaunches.add(app)
                activeApp = app
                playbackStatus = "play"
                writer.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                writer.flush()
            }
            path.startsWith("/query/media-player") -> {
                val body = buildRokuMediaPlayerXml()
                writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                writer.flush()
            }
            path.startsWith("/query/active-app") -> {
                val body = "<active-app><app id=\"1234\">$activeApp</app></active-app>"
                writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                writer.flush()
            }
            path == "/" || path == "/query/device-info" -> {
                val body = "<device-info><model-name>$modelName</model-name><friendly-device-name>$modelName</friendly-device-name></device-info>"
                writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body")
                writer.flush()
            }
            else -> {
                writer.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                writer.flush()
            }
        }
    }

    private fun handleSamsungRest(sock: Socket) {
        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
        val line = reader.readLine() ?: return
        val path = line.split(" ").getOrNull(1) ?: "/"
        val writer = OutputStreamWriter(sock.getOutputStream())

        if (path.startsWith("/api/v2/")) {
            val json = """{"device":{"modelName":"$modelName","name":"$modelName","wifiMac":"00:11:22:33:44:55"}}"""
            writer.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${json.length}\r\nConnection: close\r\n\r\n$json")
            writer.flush()
        } else {
            writer.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            writer.flush()
        }
    }

    private fun handleLgWebOs(sock: Socket) {
        // webOS handshake probe acknowledgment
        val writer = OutputStreamWriter(sock.getOutputStream())
        writer.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n")
        writer.flush()
    }

    private fun handleAdbWire(sock: Socket) {
        // Line-based command emulator for ADB bridge testing
        val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
        val writer = OutputStreamWriter(sock.getOutputStream())
        while (isRunning.get() && !sock.isClosed) {
            val cmd = reader.readLine() ?: break
            receivedShellCommands.add(cmd)
            when {
                cmd.contains("dumpsys media_session") -> {
                    writer.write(generateAdbDumpsysOutput() + "\n===END===\n")
                    writer.flush()
                }
                cmd.contains("input keyevent") -> {
                    val keycode = cmd.substringAfter("input keyevent").trim()
                    receivedKeypresses.add(keycode)
                    writer.write("OK\n===END===\n")
                    writer.flush()
                }
                cmd.contains("am start") -> {
                    receivedLaunches.add(cmd)
                    writer.write("Starting: Intent ...\n===END===\n")
                    writer.flush()
                }
                else -> {
                    writer.write("OK\n===END===\n")
                    writer.flush()
                }
            }
        }
    }

    private fun buildRokuMediaPlayerXml(): String {
        return """
            <player error="false" state="$playbackStatus">
                <plugin band="0" id="1234" name="$activeApp" />
                <position>$playheadMs ms</position>
                <duration>7200000 ms</duration>
            </player>
        """.trimIndent()
    }

    fun generateAdbDumpsysOutput(): String {
        val p = personality
        if (p is TvPersonality.ProfilePickerInterception) {
            return """
                Display Power: state=ON
                mWakefulness=Awake
                package=dev.cobalt.app
                  state=PlaybackState {state=3, position=-1, buffered position=0, speed=0.0, updated=1000}
                  description=Who's Watching?, Account Selection
            """.trimIndent()
        }

        val stateCode = if (playbackStatus == "play") 3 else 2
        val speed = if (playbackStatus == "play") 1.0f else 0.0f
        return """
            Display Power: state=ON
            mWakefulness=Awake
            mLastWakeTime=5000 (1000 ms ago)
            package=dev.cobalt.app
              state=PlaybackState {state=$stateCode, position=$playheadMs, buffered position=120000, speed=$speed, updated=5500}
              description=Sovereign Cinema Stream, High Definition
        """.trimIndent()
    }

    override fun close() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        try {
            workerThread?.interrupt()
        } catch (_: Exception) {}
    }
}
