package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.model.CinemaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Sovereign Cinema Beam Planner:
 * One Job: Resolves canonical video streams and constructs display-waking, session-preserving
 * ADB / DIAL launch intents across disparate TV platforms (Fire TV, Google TV, Roku)
 * for all supported CinemaSources (YouTube, Prime Video, Pluto TV, Direct Stream).
 */
object CinemaBeamPlanner {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    suspend fun resolveMediaUri(
        input: String,
        source: CinemaSource = CinemaSource.YOUTUBE,
        client: OkHttpClient = httpClient
    ): String = withContext(Dispatchers.IO) {
        val raw = input.trim().take(256)
        if (raw.isBlank()) return@withContext raw

        // Sovereign auto-prefixing for bare domain links
        val trimmed = if (raw.startsWith("youtu.be/") || raw.startsWith("youtube.com/") || raw.startsWith("www.")) {
            "https://$raw"
        } else raw

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return@withContext trimmed
        }

        // Direct 11-character YouTube video ID optimization (0ms local resolution)
        if (source == CinemaSource.YOUTUBE && trimmed.matches(Regex("""^[a-zA-Z0-9_-]{11}$"""))) {
            return@withContext "https://www.youtube.com/watch?v=$trimmed"
        }

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        when (source) {
            CinemaSource.YOUTUBE -> {
                try {
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
                "https://www.youtube.com/results?search_query=$encoded"
            }
            CinemaSource.PRIME_VIDEO -> "https://watch.amazon.com/watch?search=$encoded"
            CinemaSource.PLUTO_TV -> {
                if (trimmed.isBlank() || trimmed.equals("Live Guide", ignoreCase = true)) {
                    "https://pluto.tv/live-tv"
                } else {
                    "https://pluto.tv/search/$encoded"
                }
            }
            CinemaSource.DIRECT_URL -> trimmed
        }
    }

    /**
     * Source-agnostic origin synchronizer: constructs a zero-offset seek URI for lockstep party alignment
     * across disparate hardware displays without hardcoding vendor-specific patterns in caller lifecycles.
     */
    fun buildOriginSyncUri(
        canonicalUri: String,
        source: CinemaSource,
        offsetSeconds: Long = 0L
    ): String {
        return when (source) {
            CinemaSource.YOUTUBE -> {
                if (canonicalUri.contains("watch?v=") && !canonicalUri.contains("&t=")) {
                    "$canonicalUri&t=${offsetSeconds}s"
                } else canonicalUri
            }
            CinemaSource.DIRECT_URL,
            CinemaSource.PRIME_VIDEO,
            CinemaSource.PLUTO_TV -> canonicalUri
        }
    }

    fun buildAdbBeamCommand(
        uriOrQuery: String,
        isFireTv: Boolean,
        source: CinemaSource = CinemaSource.YOUTUBE,
        startOffsetSeconds: Long = 0L
    ): String {
        val trimmed = uriOrQuery.trim().take(256)
        if (!source.isInteractiveGuide) {
            require(trimmed.isNotBlank()) { "Media query or URL cannot be blank" }
        }
        val rawSafe = trimmed.replace(Regex("""['"`$\\;\r\n\t|&<>]"""), "")
        val safeInput = if (rawSafe.startsWith("youtu.be/") || rawSafe.startsWith("youtube.com/") || rawSafe.startsWith("www.")) {
            "https://$rawSafe"
        } else rawSafe
        val encoded = URLEncoder.encode(safeInput, "UTF-8")

        val targetUrl = when (source) {
            CinemaSource.YOUTUBE -> {
                val base = if (safeInput.startsWith("http://") || safeInput.startsWith("https://")) {
                    safeInput
                } else if (safeInput.matches(Regex("""^[a-zA-Z0-9_-]{11}$"""))) {
                    "https://www.youtube.com/watch?v=$safeInput"
                } else {
                    "https://www.youtube.com/results?search_query=$encoded"
                }
                if (startOffsetSeconds > 0L && base.contains("watch?v=") && !base.contains("&t=")) {
                    "${base}&t=${startOffsetSeconds}s"
                } else base
            }
            CinemaSource.PRIME_VIDEO -> {
                if (safeInput.startsWith("http://") || safeInput.startsWith("https://")) safeInput
                else "https://watch.amazon.com/watch?search=$encoded"
            }
            CinemaSource.PLUTO_TV -> {
                if (safeInput.startsWith("http://") || safeInput.startsWith("https://")) safeInput
                else "https://pluto.tv/search/$encoded"
            }
            CinemaSource.DIRECT_URL -> safeInput
        }

        return buildString {
            append("input keyevent 224; ")
            append("am force-stop com.instagram.airwave 2>/dev/null || true; ")

            when (source) {
                CinemaSource.YOUTUBE -> {
                    val ytPkg = if (isFireTv) "com.amazon.firetv.youtube" else "com.google.android.youtube.tv"
                    val ytActivity = if (isFireTv) "$ytPkg/dev.cobalt.app.MainActivity"
                    else "$ytPkg/com.google.android.apps.youtube.tv.activity.ShellActivity"
                    append("am start -a android.intent.action.VIEW -d '$targetUrl' --activity-single-top $ytActivity ")
                    append("|| am start -a android.intent.action.VIEW -d '$targetUrl' --activity-single-top $ytPkg ")
                    append("|| am start -a android.intent.action.VIEW -d '$targetUrl'")
                }
                CinemaSource.PRIME_VIDEO -> {
                    if (isFireTv) {
                        append("am start -a android.intent.action.VIEW -d '$targetUrl' com.amazon.avod ")
                        append("|| am start -n com.amazon.avod/com.amazon.avod.client.activity.HomeScreenActivity ")
                        append("|| am start -a android.intent.action.VIEW -d '$targetUrl'")
                    } else {
                        val primePkg = "com.amazon.amazonvideo.livingroom"
                        append("am start -a android.intent.action.VIEW -d '$targetUrl' $primePkg ")
                        append("|| am start -n $primePkg/com.amazon.ignition.IgnitionActivity ")
                        append("|| am start -a android.intent.action.VIEW -d '$targetUrl'")
                    }
                }
                CinemaSource.PLUTO_TV -> {
                    val plutoPkg = "tv.pluto.android"
                    if (safeInput.isBlank() || safeInput.equals("Live Guide", ignoreCase = true) || safeInput.contains("live-tv")) {
                        append("am start -n $plutoPkg/.activity.SplashActivity ")
                        append("|| monkey -p $plutoPkg -c android.intent.category.LAUNCHER 1")
                    } else {
                        append("am start -a android.intent.action.VIEW -d '$targetUrl' $plutoPkg ")
                        append("|| am start -n $plutoPkg/.activity.SplashActivity ")
                        append("|| monkey -p $plutoPkg -c android.intent.category.LAUNCHER 1")
                    }
                }
                CinemaSource.DIRECT_URL -> {
                    append("am start -a android.intent.action.VIEW -d '$targetUrl' -t 'video/*'")
                }
            }
        }
    }
}
