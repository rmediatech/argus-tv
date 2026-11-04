package com.rmediatech.sovereignremote.state

import android.content.Context
import android.content.SharedPreferences
import com.rmediatech.sovereignremote.logging.SovereignLogger
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sovereign Watch Party Session Store (SST):
 * One Job: Persists and auto-rehydrates active Watch Party cluster topology
 * (leader IP, follower IPs, and timestamp) across app restarts, phone calls, or process death.
 * Enables seamless re-attachment to ongoing TV playback sessions with zero state loss.
 */
class WatchPartySessionStore(private val prefs: SharedPreferences? = null) {

    constructor(context: Context) : this(
        try {
            context.getSharedPreferences("sovereign_watch_party", Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    )

    private val inMemoryMap = mutableMapOf<String, String>()

    data class ActivePartySession(
        val leaderIp: String,
        val followerIps: Set<String>,
        val lastActiveMs: Long = System.currentTimeMillis()
    )

    fun saveSession(leaderIp: String, followerIps: Set<String>) {
        if (followerIps.isEmpty()) {
            clearSession()
            return
        }
        val json = JSONObject().apply {
            put("leaderIp", leaderIp)
            put("followers", JSONArray(followerIps))
            put("updatedAt", System.currentTimeMillis())
        }
        val p = prefs
        if (p != null) {
            p.edit().putString(KEY_ACTIVE_PARTY, json.toString()).apply()
        } else {
            staticFallbackMap[KEY_ACTIVE_PARTY] = json.toString()
        }
        SovereignLogger.i(
            SovereignLogger.Category.LIFECYCLE,
            "Persisted WatchParty session: leader=$leaderIp, followers=$followerIps"
        )
    }

    fun loadSession(): ActivePartySession? {
        val raw = prefs?.getString(KEY_ACTIVE_PARTY, null) ?: staticFallbackMap[KEY_ACTIVE_PARTY] ?: return null
        return try {
            val json = JSONObject(raw)
            val leader = json.optString("leaderIp")
            val arr = json.optJSONArray("followers") ?: JSONArray()
            val followers = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val ip = arr.optString(i)
                if (ip.isNotBlank()) followers.add(ip)
            }
            val updatedAt = json.optLong("updatedAt", 0L)
            if (leader.isNotBlank() && followers.isNotEmpty()) {
                ActivePartySession(leader, followers, updatedAt)
            } else null
        } catch (e: Exception) {
            SovereignLogger.w(SovereignLogger.Category.LIFECYCLE, "Corrupted watch party session store: ${e.message}")
            null
        }
    }

    fun clearSession() {
        prefs?.edit()?.remove(KEY_ACTIVE_PARTY)?.apply()
        staticFallbackMap.remove(KEY_ACTIVE_PARTY)
        SovereignLogger.i(SovereignLogger.Category.LIFECYCLE, "Cleared WatchParty session persistence")
    }

    companion object {
        private const val KEY_ACTIVE_PARTY = "active_party_json"
        private val staticFallbackMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    }
}
