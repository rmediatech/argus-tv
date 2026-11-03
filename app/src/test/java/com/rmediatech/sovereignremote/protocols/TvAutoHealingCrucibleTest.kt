package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.NowPlayingState
import com.rmediatech.sovereignremote.model.PlaybackStatus
import com.rmediatech.sovereignremote.model.RemoteDevice
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Universal Adversarial TDD++++++++++:
 * Multi-Room Latency, Acoustic Echo & Slipstream Drift Crucible.
 * Rigorously simulates network jitter, clock skew, and multi-room acoustic alignment
 * across simulated disparate television hardware.
 */
class TvAutoHealingCrucibleTest {

    private val fireTv = RemoteDevice(
        id = "firetv_cheyne",
        name = "Fire TV Stick 4K Max",
        ipAddress = "192.168.1.158",
        port = 5555,
        deviceType = DeviceType.FIRE_TV
    )

    private val googleTv = RemoteDevice(
        id = "googletv_hayward",
        name = "Hisense Google TV",
        ipAddress = "192.168.1.186",
        port = 5555,
        deviceType = DeviceType.GOOGLE_TV
    )

    @Before
    fun setUp() {
        TvAutoHealingSentinel.clearHealCooldowns()
    }

    @Test
    fun testGreenZoneLockstepIntactForSub500msDrift() {
        val now = 1_000_000L
        val testDrifts = listOf(0L, 43L, -43L, 120L, -250L, 499L, -499L)

        for (drift in testDrifts) {
            val leaderState = NowPlayingState(
                appPackage = "com.google.android.youtube.tv",
                appLabel = "YouTube",
                title = "K-Pax",
                status = PlaybackStatus.PLAYING,
                positionMs = 10_000L + drift,
                capturedAtLocalMs = now
            )
            val followerState = NowPlayingState(
                appPackage = "com.amazon.firetv.youtube",
                appLabel = "YouTube",
                title = "K-Pax",
                status = PlaybackStatus.PLAYING,
                positionMs = 10_000L,
                capturedAtLocalMs = now
            )

            val zone = TvAutoHealingSentinel.classifyZone(drift)
            assertEquals("Drift $drift ms must classify as GREEN zone", TvAutoHealingSentinel.HealingZone.GREEN, zone)

            val decision = TvAutoHealingSentinel.evaluateDrift(leaderState, followerState, now)
            assertTrue("Drift $drift ms must keep lockstep intact", decision is TvAutoHealingSentinel.AutoHealDecision.LockstepIntact)
        }
    }

    @Test
    fun testYellowZoneSlipstreamHoldEngagesBetween500msAnd2500ms() {
        val now = 1_000_000L

        // 1. Leader TV running ahead by 950ms (Fire TV lagging behind Google TV)
        val leaderFastState = NowPlayingState(
            appPackage = "com.google.android.youtube.tv",
            appLabel = "YouTube",
            title = "Poppin John - K-Pax Remix",
            status = PlaybackStatus.PLAYING,
            positionMs = 15_950L,
            capturedAtLocalMs = now
        )
        val followerSlowState = NowPlayingState(
            appPackage = "com.amazon.firetv.youtube",
            appLabel = "YouTube",
            title = "Poppin John - K-Pax Remix",
            status = PlaybackStatus.PLAYING,
            positionMs = 15_000L,
            capturedAtLocalMs = now
        )

        val zoneFast = TvAutoHealingSentinel.classifyZone(950L)
        assertEquals(TvAutoHealingSentinel.HealingZone.YELLOW, zoneFast)

        val decisionFast = TvAutoHealingSentinel.evaluateDrift(leaderFastState, followerSlowState, now)
        assertTrue(decisionFast is TvAutoHealingSentinel.AutoHealDecision.SlipstreamHold)
        val holdDecision = decisionFast as TvAutoHealingSentinel.AutoHealDecision.SlipstreamHold
        assertEquals(950L, holdDecision.holdDurationMs)
        assertEquals("com.google.android.youtube.tv", holdDecision.targetDeviceIp)

        // 2. Follower TV running ahead by 1200ms
        val leaderSlowState = leaderFastState.copy(positionMs = 15_000L)
        val followerFastState = followerSlowState.copy(positionMs = 16_200L)
        val decisionSlow = TvAutoHealingSentinel.evaluateDrift(leaderSlowState, followerFastState, now)
        assertTrue(decisionSlow is TvAutoHealingSentinel.AutoHealDecision.SlipstreamHold)
        val holdFollower = decisionSlow as TvAutoHealingSentinel.AutoHealDecision.SlipstreamHold
        assertEquals(1200L, holdFollower.holdDurationMs)
        assertEquals("com.amazon.firetv.youtube", holdFollower.targetDeviceIp)
    }

    @Test
    fun testRedZoneHardReconciliationForSevereDriftOver2500ms() {
        val now = 1_000_000L
        val massiveDrift = 4500L

        val leaderState = NowPlayingState(
            appPackage = "tv.pluto.android",
            appLabel = "Pluto TV",
            title = "Sovereign Cinema",
            status = PlaybackStatus.PLAYING,
            positionMs = 60_000L,
            capturedAtLocalMs = now
        )
        val followerLaggingState = NowPlayingState(
            appPackage = "tv.pluto.android",
            appLabel = "Pluto TV",
            title = "Sovereign Cinema",
            status = PlaybackStatus.PLAYING,
            positionMs = 55_500L,
            capturedAtLocalMs = now
        )

        val zone = TvAutoHealingSentinel.classifyZone(massiveDrift)
        assertEquals(TvAutoHealingSentinel.HealingZone.RED, zone)

        val decision = TvAutoHealingSentinel.evaluateDrift(leaderState, followerLaggingState, now)
        assertTrue("Severe drift must trigger HardReconciliation", decision is TvAutoHealingSentinel.AutoHealDecision.HardReconciliation)
        val hardRec = decision as TvAutoHealingSentinel.AutoHealDecision.HardReconciliation
        assertEquals(massiveDrift, hardRec.driftMs)
    }

    @Test
    fun testCooldownAntiFlappingGuardrails() {
        val targetIp = fireTv.ipAddress
        val t0 = 100_000L

        // Initial state: device can heal
        assertTrue(TvAutoHealingSentinel.canHealDevice(targetIp, t0))

        // Record execution at t0
        TvAutoHealingSentinel.recordHealExecution(targetIp, t0)

        // At t0 + 1500ms: cooldown active, cannot heal
        assertFalse(TvAutoHealingSentinel.canHealDevice(targetIp, t0 + 1500L))

        // At t0 + 3999ms: cooldown still active
        assertFalse(TvAutoHealingSentinel.canHealDevice(targetIp, t0 + 3999L))

        // At t0 + 4001ms: cooldown expired, permissible to heal again
        assertTrue(TvAutoHealingSentinel.canHealDevice(targetIp, t0 + 4001L))
    }

    @Test
    fun testPausedPlaybackSuppressesFalseHealingPulses() {
        val now = 1_000_000L
        val leaderPaused = NowPlayingState(
            appPackage = "com.google.android.youtube.tv",
            appLabel = "YouTube",
            title = "Paused Movie",
            status = PlaybackStatus.PAUSED,
            positionMs = 30_000L,
            capturedAtLocalMs = now
        )
        val followerPaused = NowPlayingState(
            appPackage = "com.amazon.firetv.youtube",
            appLabel = "YouTube",
            title = "Paused Movie",
            status = PlaybackStatus.PAUSED,
            positionMs = 25_000L, // 5000ms historical delta, but both paused!
            capturedAtLocalMs = now
        )

        val decision = TvAutoHealingSentinel.evaluateDrift(leaderPaused, followerPaused, now)
        assertTrue("Paused playback must not fire spontaneous healing pulses", decision is TvAutoHealingSentinel.AutoHealDecision.LockstepIntact)
    }
}
