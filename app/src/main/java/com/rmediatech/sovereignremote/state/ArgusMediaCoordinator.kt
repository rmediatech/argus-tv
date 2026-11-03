package com.rmediatech.sovereignremote.state

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rmediatech.sovereignremote.logging.SovereignLogger
import com.rmediatech.sovereignremote.model.NowPlayingState
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
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

    fun startMonitoring(
        deviceProvider: () -> RemoteDevice?,
        allDevicesProvider: () -> List<RemoteDevice>
    ) {
        telemetryJob?.cancel()
        telemetryJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val activeDevice = deviceProvider()
                val allDevs = allDevicesProvider()

                // 1. Reconcile RAII sleep-guard leases across primary TV + all Watch Party followers
                val desiredGuardedIps = buildSet {
                    if (activeDevice != null) add(activeDevice.ipAddress)
                    addAll(watchPartyIps)
                }
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
        CoroutineScope(Dispatchers.IO).launch {
            for (dev in toRelease) {
                SovereignLogger.i(SovereignLogger.Category.LIFECYCLE, "Releasing TV sleep guard on exit for ${dev.name} (${dev.ipAddress})")
                registry.releaseSleepGuard(dev)
            }
        }
    }

    fun toggleWatchPartyDevice(ip: String, allDevices: List<RemoteDevice> = emptyList()) {
        val removing = watchPartyIps.contains(ip)
        watchPartyIps = if (removing) watchPartyIps - ip else watchPartyIps + ip
        if (removing) {
            guardedDevices.remove(ip)?.let { dev ->
                scope.launch(Dispatchers.IO) { registry.releaseSleepGuard(dev) }
            }
        } else {
            allDevices.find { it.ipAddress == ip }?.let { dev ->
                guardedDevices[ip] = dev
                scope.launch(Dispatchers.IO) { registry.acquireSleepGuard(dev) }
            }
        }
    }

    /**
     * Zero-lag parallel transport fanout: when user presses Play/Pause, Rewind, or Fast-Forward
     * while Watch Party is active, dispatches immediately to all synced follower TVs in parallel.
     */
    fun fanoutTransportKeyIfWatchParty(
        key: RemoteKey,
        primaryDevice: RemoteDevice,
        allDevices: List<RemoteDevice>
    ) {
        if (watchPartyIps.isEmpty()) return
        if (key != RemoteKey.PLAY_PAUSE && key != RemoteKey.REWIND && key != RemoteKey.FAST_FORWARD) return

        val followers = allDevices.filter {
            it.ipAddress != primaryDevice.ipAddress && watchPartyIps.contains(it.ipAddress)
        }
        if (followers.isEmpty()) return

        val now = System.currentTimeMillis()
        for (follower in followers) {
            TvMediaBridge.recordFollowerSync(follower.ipAddress, now)
            scope.launch(Dispatchers.IO) {
                SovereignLogger.i(SovereignLogger.Category.PROTOCOL, "WatchParty parallel fanout $key -> ${follower.name} (${follower.ipAddress})")
                registry.sendKey(follower, key)
            }
        }
    }

    /**
     * Beams a movie search or URL simultaneously to the primary TV AND all active Watch Party TVs.
     */
    fun beamMediaToTv(
        device: RemoteDevice?,
        queryOrUrl: String,
        allDevices: List<RemoteDevice> = emptyList()
    ) {
        val target = device ?: run {
            Toast.makeText(context, "Select a TV first", Toast.LENGTH_SHORT).show()
            return
        }
        val clean = queryOrUrl.trim()
        if (clean.isBlank()) return

        val followers = allDevices.filter {
            it.ipAddress != target.ipAddress && watchPartyIps.contains(it.ipAddress)
        }
        val allTargets = listOf(target) + followers
        val label = if (followers.isEmpty()) target.name else "${allTargets.size} Watch Party TVs"
        SovereignLogger.i(SovereignLogger.Category.PROTOCOL, "Beaming media '$clean' -> $label")
        Toast.makeText(context, "Beaming to $label...", Toast.LENGTH_SHORT).show()

        scope.launch {
            val results = allTargets.map { dev ->
                async(Dispatchers.IO) { registry.launchMediaUri(dev, clean) }
            }.awaitAll()
            if (results.first().isSuccess) {
                if (followers.isNotEmpty()) {
                    // Auto-Lockstep: Buffer lock and parallel release to eliminate cold-boot discrepancy!
                    delay(3500)
                    allTargets.map { dev ->
                        async(Dispatchers.IO) { registry.sendKey(dev, RemoteKey.PLAY_PAUSE) }
                    }.awaitAll()
                    delay(1500)
                    allTargets.map { dev ->
                        async(Dispatchers.IO) { registry.sendKey(dev, RemoteKey.PLAY_PAUSE) }
                    }.awaitAll()
                } else {
                    delay(2500)
                }
                val refreshed = withContext(Dispatchers.IO) { registry.queryNowPlaying(target) }
                nowPlaying = refreshed
            } else {
                Toast.makeText(context, "Beam failed: ${results.first().exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
