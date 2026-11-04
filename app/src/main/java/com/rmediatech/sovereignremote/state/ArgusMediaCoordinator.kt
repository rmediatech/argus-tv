package com.rmediatech.sovereignremote.state

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rmediatech.sovereignremote.logging.SovereignLogger
import com.rmediatech.sovereignremote.model.CinemaSource
import com.rmediatech.sovereignremote.model.NowPlayingState
import com.rmediatech.sovereignremote.model.PlaybackStatus
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.protocols.CinemaBeamPlanner
import com.rmediatech.sovereignremote.protocols.TvAutoHealingSentinel
import com.rmediatech.sovereignremote.protocols.TvMediaBridge
import com.rmediatech.sovereignremote.protocols.TvProtocolRegistry
import kotlinx.coroutines.*

/**
 * Sovereign Argus Media & Watch-Party Coordinator:
 * One Job: Polls non-DRM hardware media_session / ECP playhead state, manages RAII sleep-inhibition
 * leases across active and Watch-Party TVs, beams synchronized movie streams, and dispatches
 * zero-lag parallel transport keys (Play/Pause, Rewind, Fast-Forward) across all Watch Party TVs.
 */
class ArgusMediaCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val registry: TvProtocolRegistry
) {
    var nowPlaying by mutableStateOf<NowPlayingState?>(null)
        private set

    var watchPartyIps by mutableStateOf<Set<String>>(emptySet())
        private set

    var followerTelemetry by mutableStateOf<Map<String, NowPlayingState>>(emptyMap())
        private set

    var healingSnapshot by mutableStateOf<TvAutoHealingSentinel.SentinelTelemetrySnapshot?>(null)
        private set

    private var telemetryJob: Job? = null
    private var guardedDevices = mutableMapOf<String, RemoteDevice>()
    private val sessionStore = WatchPartySessionStore(context)

    init {
        // Auto-rehydrate ongoing Watch Party session across app restart
        val saved = sessionStore.loadSession()
        if (saved != null) {
            watchPartyIps = saved.followerIps
            SovereignLogger.i(
                SovereignLogger.Category.LIFECYCLE,
                "Rehydrated ongoing WatchParty session: leader=${saved.leaderIp}, followers=${saved.followerIps}"
            )
        }
    }

    fun startMonitoring(
        deviceProvider: () -> RemoteDevice?,
        allDevicesProvider: () -> List<RemoteDevice>,
        onAutoSelectLeader: ((RemoteDevice) -> Unit)? = null
    ) {
        telemetryJob?.cancel()
        // If rehydrated session has a leader different from current, promote leader if valid
        val saved = sessionStore.loadSession()
        if (saved != null && onAutoSelectLeader != null) {
            val allDevs = allDevicesProvider()
            val savedLeader = allDevs.find { it.ipAddress == saved.leaderIp }
            val current = deviceProvider()
            if (savedLeader != null && current?.ipAddress != savedLeader.ipAddress) {
                SovereignLogger.i(SovereignLogger.Category.LIFECYCLE, "Restoring active WatchParty primary focus to leader: ${savedLeader.name}")
                onAutoSelectLeader(savedLeader)
            }
        }
        telemetryJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val activeDevice = deviceProvider()
                val allDevs = allDevicesProvider()

                // 1. Reconcile RAII sleep-guard leases strictly when Watch Party is engaged
                val desiredGuardedIps = if (watchPartyIps.isNotEmpty() && activeDevice != null) {
                    buildSet {
                        add(activeDevice.ipAddress)
                        addAll(watchPartyIps)
                    }
                } else emptySet()
                val currentGuardedIps = guardedDevices.keys.toSet()
                for (removedIp in (currentGuardedIps - desiredGuardedIps)) {
                    guardedDevices.remove(removedIp)?.let { dev ->
                        SovereignLogger.i(SovereignLogger.Category.LIFECYCLE, "Releasing TV sleep guard on ${dev.name} (${dev.ipAddress})")
                        registry.releaseSleepGuard(dev)
                    }
                }
                for (addedIp in (desiredGuardedIps - currentGuardedIps)) {
                    val dev = (if (activeDevice?.ipAddress == addedIp) activeDevice else allDevs.find { it.ipAddress == addedIp })
                    if (dev != null) {
                        guardedDevices[addedIp] = dev
                        SovereignLogger.i(SovereignLogger.Category.LIFECYCLE, "Acquiring TV sleep guard on ${dev.name} (${dev.ipAddress})")
                        registry.acquireSleepGuard(dev)
                    }
                }

                // 2. Poll leader & synchronize Watch Party followers
                if (activeDevice == null) {
                    withContext(Dispatchers.Main) {
                        nowPlaying = null
                        followerTelemetry = emptyMap()
                        healingSnapshot = null
                    }
                } else {
                    val state = registry.queryNowPlaying(activeDevice)
                    val followers = allDevs.filter {
                        it.ipAddress != activeDevice.ipAddress && watchPartyIps.contains(it.ipAddress)
                    }
                    val fStates = mutableMapOf<String, NowPlayingState>()
                    var latestSnapshot: TvAutoHealingSentinel.SentinelTelemetrySnapshot? = null

                    if (state != null && followers.isNotEmpty()) {
                        fStates.putAll(TvMediaBridge.syncWatchPartyFollowers(state, followers, registry))
                        val firstFollower = followers.first()
                        val followerState = fStates[firstFollower.ipAddress]
                        if (followerState != null) {
                            latestSnapshot = TvAutoHealingSentinel.executeClosedLoopHealing(
                                leaderDevice = activeDevice,
                                leaderState = state,
                                followerDevice = firstFollower,
                                followerState = followerState,
                                registry = registry
                            )
                        }
                    }
                    withContext(Dispatchers.Main) {
                        nowPlaying = state
                        followerTelemetry = fStates
                        healingSnapshot = latestSnapshot
                    }
                }
                delay(3000)
            }
        }
    }

    fun releaseActiveSleepGuard() {
        val toRelease = guardedDevices.values.toList()
        guardedDevices.clear()
        if (toRelease.isEmpty()) return
        scope.launch {
            for (dev in toRelease) {
                SovereignLogger.i(SovereignLogger.Category.LIFECYCLE, "Releasing TV sleep guard on exit for ${dev.name} (${dev.ipAddress})")
                registry.releaseSleepGuard(dev)
            }
        }
    }

    fun toggleWatchPartyDevice(ip: String, allDevices: List<RemoteDevice> = emptyList(), leaderIp: String = "") {
        val removing = watchPartyIps.contains(ip)
        val updated = if (removing) watchPartyIps - ip else watchPartyIps + ip
        watchPartyIps = updated
        sessionStore.saveSession(leaderIp, updated)
        if (removing) {
            guardedDevices.remove(ip)?.let { dev ->
                scope.launch { registry.releaseSleepGuard(dev) }
            }
        } else {
            allDevices.find { it.ipAddress == ip }?.let { dev ->
                guardedDevices[ip] = dev
                scope.launch { registry.acquireSleepGuard(dev) }
            }
        }
    }

    /**
     * Seamless & Automatic Watch Party Stop + Reset:
     * Disengages all synced follower TVs, immediately restores factory power/sleep settings
     * across every linked TV model agnostically, resets nowPlaying telemetry, and clears leases.
     */
    fun stopWatchParty(allDevices: List<RemoteDevice> = emptyList()) {
        val targetIps = watchPartyIps
        watchPartyIps = emptySet()
        followerTelemetry = emptyMap()
        healingSnapshot = null
        sessionStore.clearSession()

        val toRelease = buildSet {
            addAll(guardedDevices.values)
            for (ip in targetIps) {
                allDevices.find { it.ipAddress == ip }?.let { add(it) }
            }
        }
        guardedDevices.clear()

        scope.launch {
            for (dev in toRelease) {
                SovereignLogger.i(SovereignLogger.Category.LIFECYCLE, "Seamless WatchParty Stop: resetting ${dev.name} (${dev.ipAddress}) to factory defaults")
                registry.releaseSleepGuard(dev)
            }
        }
    }

    /**
     * Preserves Watch Party connectivity when user switches primary TV focus in the dropdown:
     * swaps the old leader into followers and promotes the new focus TV to leader.
     */
    fun swapLeaderIfWatchParty(oldLeaderIp: String, newLeaderIp: String) {
        if (watchPartyIps.contains(newLeaderIp)) {
            val updated = (watchPartyIps - newLeaderIp) + oldLeaderIp
            watchPartyIps = updated
            sessionStore.saveSession(newLeaderIp, updated)
            SovereignLogger.i(SovereignLogger.Category.LIFECYCLE, "WatchParty leader swapped: $oldLeaderIp -> $newLeaderIp (followers: $watchPartyIps)")
        }
    }

    /**
     * Zero-lag parallel transport & volume fanout: when user presses Play/Pause, Rewind, Fast-Forward,
     * Volume Up/Down, or Mute while Watch Party is active, dispatches immediately to all synced follower TVs.
     */
    fun fanoutTransportKeyIfWatchParty(
        key: RemoteKey,
        primaryDevice: RemoteDevice,
        allDevices: List<RemoteDevice>
    ) {
        if (watchPartyIps.isEmpty()) return
        if (key != RemoteKey.PLAY_PAUSE && key != RemoteKey.PLAY && key != RemoteKey.PAUSE &&
            key != RemoteKey.REWIND && key != RemoteKey.FAST_FORWARD &&
            key != RemoteKey.VOLUME_UP && key != RemoteKey.VOLUME_DOWN && key != RemoteKey.MUTE) return

        val followers = allDevices.filter {
            it.ipAddress != primaryDevice.ipAddress && watchPartyIps.contains(it.ipAddress)
        }
        if (followers.isEmpty()) return

        val now = System.currentTimeMillis()
        for (follower in followers) {
            TvMediaBridge.recordFollowerSync(follower.ipAddress, now)
            scope.launch {
                SovereignLogger.i(SovereignLogger.Category.PROTOCOL, "WatchParty parallel fanout $key -> ${follower.name} (${follower.ipAddress})")
                registry.sendKey(follower, key)
            }
        }
    }

    /**
     * Beams a movie search or URL simultaneously to the primary TV AND all active Watch Party TVs,
     * overriding multi-account profile friction during pre-flight and enforcing lockstep synchronization.
     */
    fun beamMediaToTv(
        device: RemoteDevice?,
        queryOrUrl: String,
        allDevices: List<RemoteDevice> = emptyList(),
        source: CinemaSource = CinemaSource.YOUTUBE
    ): Job? {
        val target = device ?: run {
            Toast.makeText(context, "Select a TV first", Toast.LENGTH_SHORT).show()
            return null
        }
        val clean = queryOrUrl.trim()
        if (clean.isBlank()) return null

        val followers = allDevices.filter {
            it.ipAddress != target.ipAddress && watchPartyIps.contains(it.ipAddress)
        }
        val allTargets = listOf(target) + followers
        val label = if (followers.isEmpty()) target.name else "${allTargets.size} Watch Party TVs"
        SovereignLogger.i(SovereignLogger.Category.PROTOCOL, "Beaming [${source.displayName}] '$clean' -> $label")
        try {
            Toast.makeText(context, "Beaming ${source.displayName} to $label...", Toast.LENGTH_SHORT)?.show()
        } catch (_: Exception) {}

        return scope.launch {
            // 1. Resolve canonical stream URI per source
            val canonicalUri = CinemaBeamPlanner.resolveMediaUri(clean, source)
            SovereignLogger.i(SovereignLogger.Category.PROTOCOL, "Resolved [${source.displayName}] canonical: '$canonicalUri'")

            val results = allTargets.map { dev ->
                async { registry.launchMediaUri(dev, canonicalUri, source) }
            }.awaitAll()
            if (results.first().isSuccess) {
                // Allow hardware renderers to acquire media sessions naturally without blind pause/play fighting
                delay(2500)
                val refreshed = registry.queryNowPlaying(target)
                nowPlaying = refreshed
            } else {
                Toast.makeText(context, "Beam failed: ${results.first().exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
