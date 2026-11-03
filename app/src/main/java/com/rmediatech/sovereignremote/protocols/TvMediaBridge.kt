package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.model.NowPlayingState
import com.rmediatech.sovereignremote.model.PlaybackStatus
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.security.SovereignSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Sovereign TV Media Bridge & Hardware Playhead Engine:
 * One Job: Parses non-DRM hardware media_session / ECP telemetry, computes kernel-anchored
 * millisecond playheads, builds display-waking deep-link commands, and synchronizes Watch Party TVs
 * with anti-oscillation cooldowns and drift calculation.
 */
object TvMediaBridge {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    private val WAKE_TIME_REGEX = Regex("""mLastWakeTime=(\d+)\s*\((\d+)\s*ms ago\)""")
    private val PLAYBACK_STATE_REGEX = Regex(
        """state=PlaybackState\s*\{state=(\d+),\s*position=(-?\d+),\s*buffered position=\d+,\s*speed=([0-9.]+),\s*updated=(\d+)"""
    )
    private val PACKAGE_REGEX = Regex("""package=([a-zA-Z0-9_.]+)""")
    private val DESCRIPTION_REGEX = Regex("""description=([^\r\n]+)""")
    private val ROKU_STATE_REGEX = Regex("""player\s+error="[^"]*"\s+state="([^"]+)"""")
    private val ROKU_POS_REGEX = Regex("""<position>\s*(\d+)\s*ms\s*</position>""")
    private val ROKU_APP_REGEX = Regex("""<app[^>]*>(.*?)</app>""")

    private const val SYNC_COOLDOWN_MS = 3500L
    private val lastFollowerSyncMs = ConcurrentHashMap<String, Long>()

    /**
     * Parses combined output of `dumpsys power` and `dumpsys media_session` on Android TV / Fire TV / Google TV.
     */
    fun parseAdbMediaSession(
        rawOutput: String,
        capturedAtLocalMs: Long = System.currentTimeMillis()
    ): NowPlayingState? {
        if (rawOutput.isBlank()) return null

        val isAsleep = rawOutput.contains("mWakefulness=Asleep") ||
            rawOutput.contains("Display Power: state=OFF") ||
            rawOutput.contains("mScreenState=OFF")
        val isScreenAwake = !isAsleep

        val wakeMatch = WAKE_TIME_REGEX.find(rawOutput)
        val kernelUptimeMs: Long? = if (wakeMatch != null) {
            val base = wakeMatch.groupValues[1].toLongOrNull() ?: 0L
            val ago = wakeMatch.groupValues[2].toLongOrNull() ?: 0L
            base + ago
        } else null

        val blocks = rawOutput.split(Regex("""(?=\bpackage=[a-zA-Z0-9_.]+)"""))
        var bestCandidate: NowPlayingState? = null

        for (block in blocks) {
            val pkgMatch = PACKAGE_REGEX.find(block) ?: continue
            val pkg = pkgMatch.groupValues[1].trim()
            val stateMatch = PLAYBACK_STATE_REGEX.find(block) ?: continue

            val stateCode = stateMatch.groupValues[1].toIntOrNull() ?: 0
            val rawPos = stateMatch.groupValues[2].toLongOrNull() ?: -1L
            val speed = stateMatch.groupValues[3].toFloatOrNull() ?: 1.0f
            val updatedMs = stateMatch.groupValues[4].toLongOrNull() ?: 0L

            val status = PlaybackStatus.fromAndroidStateCode(stateCode)
            if (status == PlaybackStatus.IDLE && rawPos < 0L) continue

            val descRaw = DESCRIPTION_REGEX.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val cleanedTitle = descRaw
                .split(",")
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                ?.let { SovereignSanitizer.sanitizeModelName(it) ?: "" }
                .orEmpty()

            if (cleanedTitle.isBlank() && status != PlaybackStatus.PLAYING) continue

            val computedPosMs = if (status == PlaybackStatus.PLAYING && rawPos >= 0L && kernelUptimeMs != null && kernelUptimeMs >= updatedMs && updatedMs > 0L) {
                rawPos + ((kernelUptimeMs - updatedMs) * speed).toLong()
            } else {
                rawPos.coerceAtLeast(0L)
            }

            val candidate = NowPlayingState(
                appPackage = SovereignSanitizer.sanitizeAnsi(pkg).take(80),
                appLabel = friendlyAppName(pkg),
                title = cleanedTitle.ifBlank { friendlyAppName(pkg) },
                status = status,
                positionMs = computedPosMs,
                speed = speed.coerceIn(0.0f, 4.0f),
                isScreenAwake = isScreenAwake,
                capturedAtLocalMs = capturedAtLocalMs
            )

            if (status == PlaybackStatus.PLAYING && block.contains("active=true")) {
                return candidate
            }
            if (bestCandidate == null || (status == PlaybackStatus.PLAYING && bestCandidate.status != PlaybackStatus.PLAYING)) {
                bestCandidate = candidate
            }
        }

        return bestCandidate
    }

    /**
     * Parses Roku ECP `/query/media-player` and `/query/active-app` XML payloads.
     */
    fun parseRokuMediaPlayer(
        playerXml: String,
        activeAppXml: String,
        capturedAtLocalMs: Long = System.currentTimeMillis()
    ): NowPlayingState? {
        val stateStr = ROKU_STATE_REGEX.find(playerXml)?.groupValues?.get(1)?.lowercase().orEmpty()
        val status = when (stateStr) {
            "play" -> PlaybackStatus.PLAYING
            "pause" -> PlaybackStatus.PAUSED
            "buffer" -> PlaybackStatus.BUFFERING
            else -> PlaybackStatus.IDLE
        }
        val posMs = ROKU_POS_REGEX.find(playerXml)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val appName = ROKU_APP_REGEX.find(activeAppXml)?.groupValues?.get(1)
            ?.let { SovereignSanitizer.sanitizeModelName(it) ?: "" }
            .orEmpty()

        if (status == PlaybackStatus.IDLE && appName.isBlank()) return null

        return NowPlayingState(
            appPackage = "roku.ecp",
            appLabel = appName.ifBlank { "Roku Media" },
            title = appName.ifBlank { "Roku Stream" },
            status = status,
            positionMs = posMs,
            speed = if (status == PlaybackStatus.PLAYING) 1.0f else 0.0f,
            isScreenAwake = true,
            capturedAtLocalMs = capturedAtLocalMs
        )
    }

    /**
     * Resolves an arbitrary query or URL into a canonical direct video URL (e.g. watch?v=...).
     * Direct video URLs pass through immediately. Text queries query YouTube to extract the #1
     * canonical video ID so all Watch Party TVs play the identical video in lockstep.
     */
    suspend fun resolveCanonicalVideoUri(
        input: String,
        client: OkHttpClient = httpClient
    ): String = withContext(Dispatchers.IO) {
        val trimmed = input.trim().take(256)
        if (trimmed.isBlank() || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return@withContext trimmed
        }
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/results?search_query=$encoded")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val match = Regex("""/watch\?v=([a-zA-Z0-9_-]{11})""").find(resp.body?.string().orEmpty())
                    if (match != null) return@withContext "https://www.youtube.com/watch?v=${match.groupValues[1]}"
                }
            }
        } catch (_: Exception) {}
        val fallbackEncoded = URLEncoder.encode(trimmed, "UTF-8")
        "https://www.youtube.com/results?search_query=$fallbackEncoded"
    }

    /**
     * Builds an atomic, self-healing ADB shell command that:
     * 1. Wakes the HDMI display pipeline (`input keyevent 224`) and engages `svc power stayon true`.
     * 2. Purges zombie background overlay stacks (`com.instagram.airwave`).
     * 3. Launches singleTop YouTube (`com.amazon.firetv.youtube` or `com.google.android.youtube.tv`).
     */
    fun buildAdbMediaBeamCommand(
        uriOrQuery: String,
        isFireTv: Boolean,
        startOffsetSeconds: Long = 0L
    ): String {
        val trimmed = uriOrQuery.trim().take(256)
        require(trimmed.isNotBlank()) { "Media query or URL cannot be blank" }
        val safeInput = trimmed.replace(Regex("""['"`$\\;\r\n\t|&<>]"""), "")
        val baseUrl = if (safeInput.startsWith("http://") || safeInput.startsWith("https://")) {
            safeInput
        } else {
            val encoded = URLEncoder.encode(safeInput, "UTF-8")
            "https://www.youtube.com/results?search_query=$encoded"
        }
        val targetUrl = if (startOffsetSeconds > 0L && baseUrl.contains("watch?v=") && !baseUrl.contains("&t=")) {
            "${baseUrl}&t=${startOffsetSeconds}s"
        } else baseUrl

        val ytPkg = if (isFireTv) "com.amazon.firetv.youtube" else "com.google.android.youtube.tv"
        val ytActivity = if (isFireTv) {
            "$ytPkg/dev.cobalt.app.MainActivity"
        } else {
            "$ytPkg/com.google.android.apps.youtube.tv.activity.ShellActivity"
        }

        return buildString {
            append("input keyevent 224; ")
            append("svc power stayon true 2>/dev/null || true; ")
            append("am force-stop com.instagram.airwave 2>/dev/null || true; ")
            // Preserve active signed-in user profile without forcing "Who's watching?" chooser
            append("am start -a android.intent.action.VIEW -d '$targetUrl' --activity-single-top $ytActivity ")
            append("|| am start -a android.intent.action.VIEW -d '$targetUrl' --activity-single-top $ytPkg ")
            append("|| am start -a android.intent.action.VIEW -d '$targetUrl'")
        }
    }

    /**
     * Computes signed playhead drift in milliseconds (`leaderPos - followerPos`).
     */
    fun computePlayheadDriftMs(
        leader: NowPlayingState,
        follower: NowPlayingState,
        nowLocalMs: Long = System.currentTimeMillis()
    ): Long {
        return leader.livePositionMs(nowLocalMs) - follower.livePositionMs(nowLocalMs)
    }

    /**
     * Checks whether a follower TV is eligible for an automatic state sync command,
     * preventing feedback oscillation when hardware transitions take >300ms.
     */
    fun canSyncFollower(ip: String, nowMs: Long = System.currentTimeMillis()): Boolean =
        (nowMs - (lastFollowerSyncMs[ip] ?: 0L)) >= SYNC_COOLDOWN_MS

    fun recordFollowerSync(ip: String, nowMs: Long = System.currentTimeMillis()) {
        lastFollowerSyncMs[ip] = nowMs
    }

    fun clearSyncCooldowns() { lastFollowerSyncMs.clear() }

    /**
     * Synchronizes follower TVs on the LAN with the primary TV's Play/Pause state (Watch Party Sync)
     * with debounce cooldown protection against ping-pong oscillation.
     */
    suspend fun syncWatchPartyFollowers(
        leaderState: NowPlayingState,
        followers: List<RemoteDevice>,
        registry: TvProtocolRegistry,
        nowMs: Long = System.currentTimeMillis()
    ): Map<String, NowPlayingState> {
        val followerStates = mutableMapOf<String, NowPlayingState>()
        for (follower in followers) {
            val protocol = registry.get(follower.deviceType)
            val fState = protocol.queryNowPlaying(follower) ?: continue
            followerStates[follower.ipAddress] = fState

            // 1. Rogue Autoplay Suppression: If leader is paused/idle, hold followers paused
            if (leaderState.status != PlaybackStatus.PLAYING && fState.status == PlaybackStatus.PLAYING) {
                if (canSyncFollower(follower.ipAddress, nowMs)) {
                    recordFollowerSync(follower.ipAddress, nowMs)
                    protocol.sendKey(follower, RemoteKey.PLAY_PAUSE)
                }
                continue
            }

            // 2. Lockstep Playback Sync
            if ((fState.status == PlaybackStatus.PLAYING || fState.status == PlaybackStatus.PAUSED) &&
                fState.status != leaderState.status &&
                canSyncFollower(follower.ipAddress, nowMs)
            ) {
                recordFollowerSync(follower.ipAddress, nowMs)
                protocol.sendKey(follower, RemoteKey.PLAY_PAUSE)
            }
        }
        return followerStates
    }

    fun friendlyAppName(pkg: String): String = when {
        pkg.contains("youtube", ignoreCase = true) -> "YouTube"
        pkg.contains("netflix", ignoreCase = true) -> "Netflix"
        pkg.contains("avod", ignoreCase = true) || pkg.contains("prime", ignoreCase = true) -> "Prime Video"
        pkg.contains("pluto", ignoreCase = true) -> "Pluto TV"
        pkg.contains("tubi", ignoreCase = true) -> "Tubi"
        pkg.contains("plex", ignoreCase = true) -> "Plex"
        pkg.contains("disney", ignoreCase = true) -> "Disney+"
        pkg.contains("alexamediaplayer", ignoreCase = true) -> "Alexa Media"
        else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
