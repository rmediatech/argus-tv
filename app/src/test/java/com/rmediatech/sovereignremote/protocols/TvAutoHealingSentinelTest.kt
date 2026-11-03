package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.model.NowPlayingState
import com.rmediatech.sovereignremote.model.PlaybackStatus
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.protocols.TvAutoHealingSentinel.AutoHealDecision
import com.rmediatech.sovereignremote.protocols.TvAutoHealingSentinel.HealingZone
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TvAutoHealingSentinelTest {

    @Before
    fun setup() {
        TvAutoHealingSentinel.clearHealCooldowns()
    }

    @Test
    fun `zone classification partitions green, yellow, and red zones mathematically`() {
        assertEquals(HealingZone.GREEN, TvAutoHealingSentinel.classifyZone(0L))
        assertEquals(HealingZone.GREEN, TvAutoHealingSentinel.classifyZone(450L))
        assertEquals(HealingZone.GREEN, TvAutoHealingSentinel.classifyZone(-499L))

        assertEquals(HealingZone.YELLOW, TvAutoHealingSentinel.classifyZone(500L))
        assertEquals(HealingZone.YELLOW, TvAutoHealingSentinel.classifyZone(1500L))
        assertEquals(HealingZone.YELLOW, TvAutoHealingSentinel.classifyZone(-2500L))

        assertEquals(HealingZone.RED, TvAutoHealingSentinel.classifyZone(2501L))
        assertEquals(HealingZone.RED, TvAutoHealingSentinel.classifyZone(-39833L))
    }

    @Test
    fun `slipstream decision calculates correct hold duration and target device`() {
        val leader = NowPlayingState(
            appPackage = "com.amazon.firetv.youtube",
            appLabel = "YouTube",
            title = "The Matrix",
            status = PlaybackStatus.PLAYING,
            positionMs = 10_000L,
            speed = 1.0f,
            capturedAtLocalMs = 1_000L
        )
        val followerLagging = NowPlayingState(
            appPackage = "com.google.android.youtube.tv",
            appLabel = "YouTube",
            title = "The Matrix",
            status = PlaybackStatus.PLAYING,
            positionMs = 8_800L, // 1200ms behind
            speed = 1.0f,
            capturedAtLocalMs = 1_000L
        )

        val decision = TvAutoHealingSentinel.evaluateDrift(leader, followerLagging, nowLocalMs = 1_000L)
        assertTrue(decision is AutoHealDecision.SlipstreamHold)
        val hold = decision as AutoHealDecision.SlipstreamHold
        assertEquals(1200L, hold.holdDurationMs)
        assertEquals("com.amazon.firetv.youtube", hold.targetDeviceIp)
    }

    @Test
    fun `hard reconciliation triggers when drift exceeds 2500ms`() {
        val leader = NowPlayingState(
            appPackage = "com.amazon.firetv.youtube",
            appLabel = "YouTube",
            title = "The Matrix",
            status = PlaybackStatus.PLAYING,
            positionMs = 40_000L,
            speed = 1.0f,
            capturedAtLocalMs = 1_000L
        )
        val followerSevereLag = NowPlayingState(
            appPackage = "com.google.android.youtube.tv",
            appLabel = "YouTube",
            title = "The Matrix",
            status = PlaybackStatus.PLAYING,
            positionMs = 1_000L, // 39,000ms behind!
            speed = 1.0f,
            capturedAtLocalMs = 1_000L
        )

        val decision = TvAutoHealingSentinel.evaluateDrift(leader, followerSevereLag, nowLocalMs = 1_000L)
        assertTrue(decision is AutoHealDecision.HardReconciliation)
        val hard = decision as AutoHealDecision.HardReconciliation
        assertEquals(39_000L, hard.driftMs)
    }

    @Test
    fun `cooldown debouncing prevents rapid re-healing oscillations within 4000ms window`() {
        val ip = "192.168.1.158"
        assertTrue(TvAutoHealingSentinel.canHealDevice(ip, nowMs = 10_000L))
        TvAutoHealingSentinel.recordHealExecution(ip, nowMs = 10_000L)
        assertFalse("Must throttle within 4000ms heal window", TvAutoHealingSentinel.canHealDevice(ip, nowMs = 13_500L))
        assertTrue("Must allow heal after 4000ms cooldown expires", TvAutoHealingSentinel.canHealDevice(ip, nowMs = 14_500L))
    }
}
