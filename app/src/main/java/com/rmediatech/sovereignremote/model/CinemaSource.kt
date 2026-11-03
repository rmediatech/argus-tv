package com.rmediatech.sovereignremote.model

/**
 * Sovereign Cinema & Watch Party Media Source:
 * Single Source of Truth (SST) for streaming targets supported across disparate TV fleet platforms
 * (Fire TV, Google TV / Android TV, Roku ECP, and web streams).
 */
enum class CinemaSource(
    val id: String,
    val displayName: String,
    val iconEmoji: String,
    val placeholder: String
) {
    YOUTUBE(
        id = "youtube",
        displayName = "YouTube",
        iconEmoji = "🍿",
        placeholder = "e.g. Dreamcatcher, Matrix, or YouTube URL"
    ),
    PRIME_VIDEO(
        id = "prime",
        displayName = "Prime Video",
        iconEmoji = "📦",
        placeholder = "e.g. Fallout, Reacher, or Prime Video title"
    ),
    PLUTO_TV(
        id = "pluto",
        displayName = "Pluto TV",
        iconEmoji = "📺",
        placeholder = "e.g. Star Trek, 007, or channel name"
    ),
    DIRECT_URL(
        id = "direct",
        displayName = "Direct Stream",
        iconEmoji = "🌐",
        placeholder = "https://example.com/stream.mp4 or HLS m3u8"
    );

    companion object {
        val DEFAULT = YOUTUBE
        fun fromId(id: String): CinemaSource = entries.find { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}
