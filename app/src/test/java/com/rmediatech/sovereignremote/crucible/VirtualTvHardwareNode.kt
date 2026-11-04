package com.rmediatech.sovereignremote.crucible

import com.rmediatech.sovereignremote.model.*
import com.rmediatech.sovereignremote.protocols.ProtocolSignature
import com.rmediatech.sovereignremote.protocols.SmartTvProtocol
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Sovereign In-Memory Virtual TV Protocol Engine:
 * One Job: Simulates real TV hardware state machines (power, app stack, media_session,
 * volume, profile picker dialogs, and manufacturer stay-on settings) directly in memory.
 * Emulates the exact physical sequence of a user interacting with Argus:
 *   1. Discovery & Selection
 *   2. Toggling Watch Party (cyan button) -> Sleep Guard acquisition / Settings check
 *   3. Beaming stream -> Launch intent dispatch across Leader + Follower TVs
 *   4. Transport controls (Play/Pause, Volume Up/Down) -> Multi-TV fanout
 *   5. Disengaging Watch Party -> Sleep Guard release & zero-residue cleanup
 */
class VirtualTvHardwareNode(
    val deviceId: String,
    val brandName: String,
    override val deviceType: DeviceType,
    val ip: String,
    val hasProfilePickerBug: Boolean = false
) : SmartTvProtocol {

    override val protocolId: String = if (deviceType == DeviceType.FIRE_TV) "firetv" else "googletv"
    override val defaultPort: Int = 5555
    override val signature: ProtocolSignature = ProtocolSignature(
        protocolId = protocolId,
        deviceType = deviceType,
        ssdpSearchTargets = emptyList<String>(),
        ssdpHeaderMatchers = emptyList<String>(),
        candidatePorts = listOf(5555)
    )

    // Hardware State Telemetry
    var isPoweredOn: Boolean = true
    var stayOnWhilePluggedInSetting: Int = 0 // 0 = default, 7 = altered
    var sleepGuardAcquired: Boolean = false
    var activeApp: String = "com.google.android.tvlauncher"
    var isShowingProfilePicker: Boolean = false
    private var profilePickerDismissed: Boolean = false
    var playbackStatus: PlaybackStatus = PlaybackStatus.IDLE
    var playheadMs: Long = 0L
    var volumeLevel: Int = 15

    // Interactive EPG Guide Telemetry
    var isGuideActive: Boolean = false
    var channelIndex: Int = 1

    // Network & Wi-Fi Failure Simulation
    var networkLatencyMs: Long = 0L
    var simulateDropConnection: Boolean = false
    var failNextKeys: Int = 0

    // Audit logs of all commands received
    val commandLog = CopyOnWriteArrayList<String>()

    fun toRemoteDevice(): RemoteDevice = RemoteDevice(
        id = deviceId,
        name = brandName,
        ipAddress = ip,
        port = defaultPort,
        deviceType = deviceType,
        modelName = brandName,
        isEthernet = false,
        macAddress = "02:00:00:00:00:${ip.substringAfterLast('.')}"
    )

    override suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> {
        if (networkLatencyMs > 0) kotlinx.coroutines.delay(networkLatencyMs)
        if (simulateDropConnection) {
            return Result.failure(java.io.IOException("ADB socket disconnected on $ip:5555"))
        }
        if (failNextKeys > 0) {
            failNextKeys--
            return Result.failure(java.io.IOException("Transient Wi-Fi frame drop on $ip:5555"))
        }

        commandLog.add("KEY:${key.name}")
        when (key) {
            RemoteKey.POWER -> isPoweredOn = !isPoweredOn
            RemoteKey.HOME -> {
                isGuideActive = false
                isShowingProfilePicker = false
                playbackStatus = PlaybackStatus.IDLE
                activeApp = "launcher"
            }
            RemoteKey.VOLUME_UP -> volumeLevel = (volumeLevel + 1).coerceAtMost(100)
            RemoteKey.VOLUME_DOWN -> volumeLevel = (volumeLevel - 1).coerceAtLeast(0)
            RemoteKey.MUTE -> volumeLevel = 0
            RemoteKey.UP -> {
                if (isGuideActive) channelIndex = maxOf(1, channelIndex - 1)
            }
            RemoteKey.DOWN -> {
                if (isGuideActive) channelIndex++
            }
            RemoteKey.PLAY_PAUSE -> {
                playbackStatus = if (playbackStatus == PlaybackStatus.PLAYING) PlaybackStatus.PAUSED else PlaybackStatus.PLAYING
            }
            RemoteKey.PLAY -> playbackStatus = PlaybackStatus.PLAYING
            RemoteKey.PAUSE -> playbackStatus = PlaybackStatus.PAUSED
            RemoteKey.SELECT -> {
                if (isShowingProfilePicker) {
                    profilePickerDismissed = true
                    isShowingProfilePicker = false
                    playbackStatus = PlaybackStatus.PLAYING
                } else if (isGuideActive) {
                    isGuideActive = false
                    playbackStatus = PlaybackStatus.PLAYING
                    playheadMs = 1000L
                }
            }
            else -> {}
        }
        return Result.success(Unit)
    }

    override suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> {
        if (networkLatencyMs > 0) kotlinx.coroutines.delay(networkLatencyMs)
        commandLog.add("LAUNCH_APP:$appName")
        activeApp = appName
        return Result.success(Unit)
    }

    override suspend fun launchMediaUri(device: RemoteDevice, uriOrQuery: String, source: CinemaSource): Result<Unit> {
        if (networkLatencyMs > 0) kotlinx.coroutines.delay(networkLatencyMs)
        commandLog.add("BEAM_URI:$uriOrQuery:SOURCE:${source.name}")
        activeApp = source.displayName
        if (source.isInteractiveGuide) {
            isGuideActive = true
            channelIndex = 1
            playbackStatus = PlaybackStatus.BUFFERING
            isShowingProfilePicker = false
        } else if (hasProfilePickerBug && !profilePickerDismissed) {
            isShowingProfilePicker = true
            isGuideActive = false
            playbackStatus = PlaybackStatus.BUFFERING
            playheadMs = -1L
        } else {
            isShowingProfilePicker = false
            isGuideActive = false
            playbackStatus = PlaybackStatus.PLAYING
            playheadMs = 1000L
        }
        return Result.success(Unit)
    }

    override suspend fun acquireSleepGuard(device: RemoteDevice): Result<Unit> {
        commandLog.add("ACQUIRE_SLEEP_GUARD")
        sleepGuardAcquired = true
        return Result.success(Unit)
    }

    override suspend fun releaseSleepGuard(device: RemoteDevice): Result<Unit> {
        commandLog.add("RELEASE_SLEEP_GUARD")
        sleepGuardAcquired = false
        // Asserts zero residue on device settings
        stayOnWhilePluggedInSetting = 0
        return Result.success(Unit)
    }

    override suspend fun queryNowPlaying(device: RemoteDevice): NowPlayingState? {
        commandLog.add("QUERY_NOW_PLAYING")
        if (!isPoweredOn) return null
        return NowPlayingState(
            appPackage = activeApp,
            appLabel = brandName,
            title = if (isShowingProfilePicker) "Who's Watching?" else "Sovereign Movie",
            status = playbackStatus,
            positionMs = playheadMs.coerceAtLeast(0L),
            speed = if (playbackStatus == PlaybackStatus.PLAYING) 1.0f else 0.0f,
            isScreenAwake = isPoweredOn
        )
    }

    override suspend fun probe(host: String, port: Int): RemoteDevice? = if (host == ip) toRemoteDevice() else null
    override suspend fun probeSsdp(host: String, port: Int, ssdpPayload: String): RemoteDevice? = probe(host, port)
}
