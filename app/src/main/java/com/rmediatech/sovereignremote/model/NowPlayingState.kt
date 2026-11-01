package com.rmediatech.sovereignremote.model

/**
 * Sovereign Playback Lifecycle Status:
 * Maps Android MediaSession PlaybackState codes and Roku ECP player states into a unified enum.
 */
enum class PlaybackStatus(val label: String) {
    IDLE("IDLE"),
    PLAYING("PLAYING"),
    PAUSED("PAUSED"),
    BUFFERING("BUFFERING");

    companion object {
        fun fromAndroidStateCode(code: Int): PlaybackStatus = when (code) {
            3 -> PLAYING
            2 -> PAUSED
            6 -> BUFFERING
            else -> IDLE
        }
    }
}

/**
 * Sovereign Now-Playing & Hardware Clock Playhead Telemetry:
 * One Job: Immutable snapshot of active media session metadata and hardware-anchored playhead.
 * Computes live millisecond playhead position without polling the video surface or touching DRM frames.
 */
data class NowPlayingState(
    val appPackage: String,
    val appLabel: String,
    val title: String,
    val status: PlaybackStatus,
    val positionMs: Long,
    val speed: Float = 1.0f,
    val isScreenAwake: Boolean = true,
    val capturedAtLocalMs: Long = System.currentTimeMillis()
) {
    /**
     * Extrapolates the exact live playhead at [nowLocalMs] when status == PLAYING.
     */
    fun livePositionMs(nowLocalMs: Long = System.currentTimeMillis()): Long {
        if (status != PlaybackStatus.PLAYING || positionMs < 0L) {
            return positionMs.coerceAtLeast(0L)
        }
        val delta = (nowLocalMs - capturedAtLocalMs).coerceAtLeast(0L)
        return (positionMs + (delta * speed).toLong()).coerceAtLeast(0L)
    }

    /**
     * Formats the live playhead as HH:MM:SS (or MM:SS if under 1 hour).
     */
    fun formattedPlayhead(nowLocalMs: Long = System.currentTimeMillis()): String {
        val totalSeconds = livePositionMs(nowLocalMs) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}
