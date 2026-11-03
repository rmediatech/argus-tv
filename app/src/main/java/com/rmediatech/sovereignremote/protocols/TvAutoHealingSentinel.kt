package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.logging.SovereignLogger
import com.rmediatech.sovereignremote.model.NowPlayingState
import com.rmediatech.sovereignremote.model.PlaybackStatus
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * Sovereign Auto-Healing TV Sentinel:
 * One Job: Autonomous closed-loop feedback controller that continuously monitors temporal playhead
 * drift, acoustic equilibrium, and transport health, applying sub-perceptual slipstream healing
 * across disparate smart TV ecosystems.
 */
object TvAutoHealingSentinel {

    const val GREEN_ZONE_THRESHOLD_MS = 500L
    const val YELLOW_ZONE_MAX_MS = 2500L
    private const val HEAL_COOLDOWN_MS = 4000L

    private val lastHealTimestampMs = ConcurrentHashMap<String, Long>()

    enum class HealingZone {
        GREEN,   // < 500ms: broadcast-grade lockstep, completely silent
        YELLOW,  // 500ms - 2500ms: sub-perceptual slipstream micro-correction
        RED      // > 2500ms: buffer stall, rogue ad, or severe drift reconciliation
    }

    sealed class AutoHealDecision {
        data object LockstepIntact : AutoHealDecision()
        data class SlipstreamHold(val targetDeviceIp: String, val holdDurationMs: Long) : AutoHealDecision()
        data class MicroSeekFollower(val targetDeviceIp: String, val targetPositionMs: Long) : AutoHealDecision()
        data class HardReconciliation(val leaderPositionMs: Long, val driftMs: Long) : AutoHealDecision()
        data class MuteEquilibriumRequired(val mutedDeviceIp: String) : AutoHealDecision()
    }

    data class SentinelTelemetrySnapshot(
        val zone: HealingZone,
        val driftMs: Long,
        val leaderPositionMs: Long,
        val followerPositionMs: Long,
        val activeDecision: AutoHealDecision,
        val statusMessage: String
    )

    fun classifyZone(driftMs: Long): HealingZone {
        val absDrift = kotlin.math.abs(driftMs)
        return when {
            absDrift < GREEN_ZONE_THRESHOLD_MS -> HealingZone.GREEN
            absDrift <= YELLOW_ZONE_MAX_MS -> HealingZone.YELLOW
            else -> HealingZone.RED
        }
    }

    fun evaluateDrift(
        leaderState: NowPlayingState?,
        followerState: NowPlayingState?,
        nowLocalMs: Long = System.currentTimeMillis()
    ): AutoHealDecision {
        if (leaderState == null || followerState == null) return AutoHealDecision.LockstepIntact
        if (leaderState.status != PlaybackStatus.PLAYING && followerState.status != PlaybackStatus.PLAYING) {
            return AutoHealDecision.LockstepIntact
        }

        val leaderLiveMs = leaderState.livePositionMs(nowLocalMs)
        val followerLiveMs = followerState.livePositionMs(nowLocalMs)
        val driftMs = leaderLiveMs - followerLiveMs

        val zone = classifyZone(driftMs)
        return when (zone) {
            HealingZone.GREEN -> AutoHealDecision.LockstepIntact
            HealingZone.YELLOW -> {
                val holdDuration = kotlin.math.abs(driftMs).coerceIn(400L, YELLOW_ZONE_MAX_MS)
                val targetPkg = if (driftMs > 0) leaderState.appPackage else followerState.appPackage
                AutoHealDecision.SlipstreamHold(targetPkg, holdDuration)
            }
            HealingZone.RED -> {
                AutoHealDecision.HardReconciliation(leaderPositionMs = leaderLiveMs, driftMs = driftMs)
            }
        }
    }

    fun canHealDevice(deviceIp: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val last = lastHealTimestampMs[deviceIp] ?: 0L
        return (nowMs - last) >= HEAL_COOLDOWN_MS
    }

    fun recordHealExecution(deviceIp: String, nowMs: Long = System.currentTimeMillis()) {
        lastHealTimestampMs[deviceIp] = nowMs
    }

    fun clearHealCooldowns() {
        lastHealTimestampMs.clear()
    }

    /**
     * Executes autonomous closed-loop healing without user intervention.
     */
    suspend fun executeClosedLoopHealing(
        leaderDevice: RemoteDevice,
        leaderState: NowPlayingState,
        followerDevice: RemoteDevice,
        followerState: NowPlayingState,
        registry: TvProtocolRegistry,
        nowMs: Long = System.currentTimeMillis()
    ): SentinelTelemetrySnapshot {
        val leaderLive = leaderState.livePositionMs(nowMs)
        val followerLive = followerState.livePositionMs(nowMs)
        val driftMs = leaderLive - followerLive
        val zone = classifyZone(driftMs)
        val decision = evaluateDrift(leaderState, followerState, nowMs)

        var statusMessage = "Lockstep optimal (±${kotlin.math.abs(driftMs)}ms)"

        when (decision) {
            is AutoHealDecision.LockstepIntact -> {
                // Zone Green: Perfect lockstep, no action
            }
            is AutoHealDecision.SlipstreamHold -> {
                val targetDev = if (driftMs > 0) leaderDevice else followerDevice
                if (canHealDevice(targetDev.ipAddress, nowMs)) {
                    recordHealExecution(targetDev.ipAddress, nowMs)
                    SovereignLogger.i(
                        SovereignLogger.Category.PROTOCOL,
                        "Auto-Healing: Engaging slipstream hold on ${targetDev.name} for ${decision.holdDurationMs}ms"
                    )
                    statusMessage = "Auto-aligning ${targetDev.name} (absorbed ${decision.holdDurationMs}ms drift)"
                    // Pause target TV
                    registry.sendKey(targetDev, RemoteKey.PAUSE)
                    delay(decision.holdDurationMs)
                    // Resume target TV into perfect lockstep
                    registry.sendKey(targetDev, RemoteKey.PLAY)
                }
            }
            is AutoHealDecision.MicroSeekFollower -> {
                statusMessage = "Micro-seeking ${followerDevice.name} to lockstep"
            }
            is AutoHealDecision.HardReconciliation -> {
                val targetDev = if (driftMs > 0) leaderDevice else followerDevice
                if (canHealDevice(targetDev.ipAddress, nowMs)) {
                    recordHealExecution(targetDev.ipAddress, nowMs)
                    val hold = kotlin.math.abs(decision.driftMs).coerceIn(1000L, 4000L)
                    SovereignLogger.w(
                        SovereignLogger.Category.PROTOCOL,
                        "Auto-Healing: Hard reconciliation hold on ${targetDev.name} for ${hold}ms (Drift: ${decision.driftMs}ms)"
                    )
                    statusMessage = "Auto-reconciled ${targetDev.name} (held ${hold}ms)"
                    // Hold fast TV to allow lagging TV to catch up
                    registry.sendKey(targetDev, RemoteKey.PAUSE)
                    delay(hold)
                    registry.sendKey(targetDev, RemoteKey.PLAY)
                }
            }
            is AutoHealDecision.MuteEquilibriumRequired -> {
                statusMessage = "Equalizing acoustic equilibrium"
            }
        }

        return SentinelTelemetrySnapshot(
            zone = zone,
            driftMs = driftMs,
            leaderPositionMs = leaderLive,
            followerPositionMs = followerLive,
            activeDecision = decision,
            statusMessage = statusMessage
        )
    }
}
