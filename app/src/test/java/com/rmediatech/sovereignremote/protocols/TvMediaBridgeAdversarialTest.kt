package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.model.PlaybackStatus
import org.junit.Assert.*
import org.junit.Test

class TvMediaBridgeAdversarialTest {

    @Test
    fun `parses real Fire TV dumpsys power and media_session with millisecond playhead extrapolation`() {
        val rawDump = """
            mWakefulness=Awake
            Display Power: state=ON
            mLastWakeTime=946206544 (131431 ms ago)
            package=com.amazon.firetv.youtube
            active=true
            state=PlaybackState {state=3, position=2798, buffered position=0, speed=1.0, updated=946175947, actions=383}
            metadata:size=4, description=Needful Things 1993, Joe, null
        """.trimIndent()

        val state = TvMediaBridge.parseAdbMediaSession(rawDump, capturedAtLocalMs = 1_000_000L)
        assertNotNull(state)
        assertEquals("com.amazon.firetv.youtube", state!!.appPackage)
        assertEquals("YouTube", state.appLabel)
        assertEquals("Needful Things 1993", state.title)
        assertEquals(PlaybackStatus.PLAYING, state.status)
        assertTrue(state.isScreenAwake)
        // Expected kernelUptimeMs = 946206544 + 131431 = 946337975
        // Expected playhead = 2798 + (946337975 - 946175947) * 1.0 = 164826 ms (02:44)
        assertEquals(164826L, state.positionMs)
        assertEquals("02:44", state.formattedPlayhead(1_000_000L))
        // 16 seconds later -> 03:00
        assertEquals("03:00", state.formattedPlayhead(1_016_000L))
    }

    @Test
    fun `detects sleeping display when audio plays on a black screen`() {
        val rawDump = """
            mWakefulness=Asleep
            Display Power: state=OFF
            mScreenState=OFF
            mLastWakeTime=946000000 (10000 ms ago)
            package=com.amazon.firetv.youtube
            active=true
            state=PlaybackState {state=3, position=5000, buffered position=0, speed=1.0, updated=946005000, actions=383}
            metadata:size=4, description=Stephen King Storm of the Century, Royal, null
        """.trimIndent()

        val state = TvMediaBridge.parseAdbMediaSession(rawDump)
        assertNotNull(state)
        assertFalse("Should flag screen as sleeping so protocol self-heals via KEYCODE_WAKEUP", state!!.isScreenAwake)
        assertEquals(PlaybackStatus.PLAYING, state.status)
        assertEquals(10000L, state.positionMs)
    }

    @Test
    fun `buildAdbMediaBeamCommand wakes display, clears airwave overlay, targets Cobalt, and scrubs shell injection`() {
        val hostileInput = "Needful Things 1993'; rm -rf /data; $(reboot) `id` | cat /etc/passwd"
        val cmd = TvMediaBridge.buildAdbMediaBeamCommand(hostileInput, isFireTv = true)

        assertTrue(cmd.contains("input keyevent 224"))
        assertTrue(cmd.contains("am force-stop com.instagram.airwave"))
        assertTrue(cmd.contains("com.amazon.firetv.youtube/dev.cobalt.app.MainActivity"))
        assertFalse("Shell injection single-quote breakout must be stripped", cmd.contains("';"))
        assertFalse("Command substitution must be stripped", cmd.contains("$("))
        assertFalse("Backticks must be stripped", cmd.contains("`"))
        assertFalse("Pipe injection must be stripped", cmd.contains("| cat"))
    }

    @Test
    fun `parses Roku ECP media player and scrubs ANSI and formula injection payloads`() {
        val playerXml = """<player error="false" state="play"><position>3661000 ms</position></player>"""
        val appXml = """<active-app><app id="837">=cmd|' /C calc'!A0\u001B[31mYouTube</app></active-app>"""

        val state = TvMediaBridge.parseRokuMediaPlayer(playerXml, appXml, capturedAtLocalMs = 5000L)
        assertNotNull(state)
        assertEquals(PlaybackStatus.PLAYING, state!!.status)
        assertEquals(3_661_000L, state.positionMs)
        assertEquals("01:01:01", state.formattedPlayhead(5000L))
        assertTrue("Formula payload must be single-quote neutralized", state.title.startsWith("'="))
    }

    @Test
    fun `watch party debounce cooldown prevents feedback oscillation loops and computes drift`() {
        TvMediaBridge.clearSyncCooldowns()
        val ip = "192.168.1.186"
        assertTrue(TvMediaBridge.canSyncFollower(ip, nowMs = 10_000L))
        TvMediaBridge.recordFollowerSync(ip, nowMs = 10_000L)
        assertFalse("Must block duplicate sync within 3500ms cooldown window", TvMediaBridge.canSyncFollower(ip, nowMs = 12_000L))
        assertTrue("Must allow sync after 3500ms cooldown expires", TvMediaBridge.canSyncFollower(ip, nowMs = 13_600L))

        val cmdWithOffset = TvMediaBridge.buildAdbMediaBeamCommand(
            "https://www.youtube.com/watch?v=MflAz-NR_bw",
            isFireTv = true,
            startOffsetSeconds = 840L
        )
        assertTrue(cmdWithOffset.contains("svc power stayon true"))
        assertTrue(cmdWithOffset.contains("https://www.youtube.com/watch?v=MflAz-NR_bw&t=840s"))
    }
}
