package com.rmediatech.sovereignremote.crucible

import android.app.Application
import com.rmediatech.sovereignremote.model.*
import com.rmediatech.sovereignremote.protocols.CinemaBeamPlanner
import com.rmediatech.sovereignremote.protocols.TvAutoHealingSentinel
import com.rmediatech.sovereignremote.protocols.TvProtocolRegistry
import com.rmediatech.sovereignremote.state.ArgusMediaCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Sovereign Living Room Fleet Crucible Test:
 * One Job: Simulates physical living room and bedroom smart TV fleet dynamics under real-world
 * conditions (interactive EPG guide navigation, source switching friction, asymmetrical Wi-Fi jitter,
 * transient packet drops, playhead drift zones, and atomic unified teardowns).
 * 
 * Verifies that concrete implementations deliver sovereign superiority:
 *   1. Zero cloud dependencies
 *   2. Zero desynchronization between living room and bedroom displays
 *   3. Zero residual setting tampering on host hardware
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SovereignLivingRoomFleetCrucibleTest {

    @Test
    fun testInteractiveGuide_LockstepChannelBrowsingAndParallelTuning() = runTest {
        val app = Application()
        val registry = TvProtocolRegistry(context = null)
        val livingRoomGoogleTv = VirtualTvHardwareNode("living_room_gtv", "Hisense Google TV", DeviceType.GOOGLE_TV, "192.168.1.186")
        val bedroomFireTv = VirtualTvHardwareNode("bedroom_ftv", "Fire TV Stick 4K Max", DeviceType.FIRE_TV, "192.168.1.158")
        registry.register(livingRoomGoogleTv)
        registry.register(bedroomFireTv)

        val allDevices = listOf(livingRoomGoogleTv.toRemoteDevice(), bedroomFireTv.toRemoteDevice())
        val coordinator = ArgusMediaCoordinator(context = app, scope = this, registry = registry)

        // 1. Establish Watch Party (Google TV = Leader, Fire TV = Follower)
        coordinator.toggleWatchPartyDevice(bedroomFireTv.ip, allDevices, leaderIp = livingRoomGoogleTv.ip)
        testScheduler.runCurrent()
        assertTrue("Follower Fire TV must be linked in watch party", coordinator.watchPartyIps.contains(bedroomFireTv.ip))

        // 2. Beam Pluto TV (Interactive Live Guide - blank query defaults to Live Guide)
        val beamJob = coordinator.beamMediaToTv(
            device = livingRoomGoogleTv.toRemoteDevice(),
            queryOrUrl = "",
            allDevices = allDevices,
            source = CinemaSource.PLUTO_TV
        )
        beamJob?.join()
        testScheduler.runCurrent()

        assertEquals(CinemaSource.PLUTO_TV, coordinator.activeSource)
        assertTrue("Pluto TV must be classified as interactive guide", coordinator.activeSource?.isInteractiveGuide == true)
        assertTrue("Living room TV must be in guide mode", livingRoomGoogleTv.isGuideActive)
        assertTrue("Bedroom TV must be in guide mode", bedroomFireTv.isGuideActive)
        assertEquals("Both TVs start on Channel 1", 1, livingRoomGoogleTv.channelIndex)
        assertEquals("Both TVs start on Channel 1", 1, bedroomFireTv.channelIndex)

        // 3. User browses guide: taps DOWN 4 times to reach Channel 5
        for (step in 1..4) {
            livingRoomGoogleTv.sendKey(livingRoomGoogleTv.toRemoteDevice(), RemoteKey.DOWN)
            coordinator.fanoutKeyIfWatchParty(RemoteKey.DOWN, livingRoomGoogleTv.toRemoteDevice(), allDevices)
        }
        testScheduler.runCurrent()

        assertEquals("Living Room TV moved to Channel 5", 5, livingRoomGoogleTv.channelIndex)
        assertEquals("Bedroom TV moved to Channel 5 in lockstep", 5, bedroomFireTv.channelIndex)

        // 4. User selects Channel 5: both TVs tune and begin playback in lockstep!
        livingRoomGoogleTv.sendKey(livingRoomGoogleTv.toRemoteDevice(), RemoteKey.SELECT)
        coordinator.fanoutKeyIfWatchParty(RemoteKey.SELECT, livingRoomGoogleTv.toRemoteDevice(), allDevices)
        testScheduler.runCurrent()

        assertFalse("Living Room TV exited guide mode into active playback", livingRoomGoogleTv.isGuideActive)
        assertFalse("Bedroom TV exited guide mode into active playback", bedroomFireTv.isGuideActive)
        assertEquals(PlaybackStatus.PLAYING, livingRoomGoogleTv.playbackStatus)
        assertEquals(PlaybackStatus.PLAYING, bedroomFireTv.playbackStatus)

        // Teardown
        coordinator.stopWatchParty(allDevices)
        testScheduler.runCurrent()
    }

    @Test
    fun testRapidSourceSwitching_PlutoGuideToYouTubeVideoToDirectHlsStream() = runTest {
        val app = Application()
        val registry = TvProtocolRegistry(context = null)
        val leader = VirtualTvHardwareNode("leader_tv", "Leader Google TV", DeviceType.GOOGLE_TV, "192.168.1.186")
        val follower = VirtualTvHardwareNode("follower_tv", "Follower Fire TV", DeviceType.FIRE_TV, "192.168.1.158")
        registry.register(leader)
        registry.register(follower)

        val allDevices = listOf(leader.toRemoteDevice(), follower.toRemoteDevice())
        val coordinator = ArgusMediaCoordinator(context = app, scope = this, registry = registry)

        coordinator.toggleWatchPartyDevice(follower.ip, allDevices, leaderIp = leader.ip)
        testScheduler.runCurrent()

        // 1. Start with Pluto TV (Guide mode)
        val plutoJob = coordinator.beamMediaToTv(leader.toRemoteDevice(), "", allDevices, CinemaSource.PLUTO_TV)
        plutoJob?.join()
        testScheduler.runCurrent()
        assertTrue(coordinator.activeSource?.isInteractiveGuide == true)

        // D-pad fans out
        coordinator.fanoutKeyIfWatchParty(RemoteKey.DOWN, leader.toRemoteDevice(), allDevices)
        testScheduler.runCurrent()
        assertEquals(2, follower.channelIndex)

        // 2. Switch to YouTube (Stream mode)
        val ytJob = coordinator.beamMediaToTv(leader.toRemoteDevice(), "https://www.youtube.com/watch?v=dQw4w9WgXcQ", allDevices, CinemaSource.YOUTUBE)
        ytJob?.join()
        testScheduler.runCurrent()
        assertEquals(CinemaSource.YOUTUBE, coordinator.activeSource)
        assertFalse("YouTube is not an interactive guide", coordinator.activeSource?.isInteractiveGuide == true)

        // D-pad fans out across all sources during Watch Party (Universal Parity)
        val followerDownCount = follower.commandLog.count { it == "KEY:DOWN" }
        coordinator.fanoutKeyIfWatchParty(RemoteKey.DOWN, leader.toRemoteDevice(), allDevices)
        testScheduler.runCurrent()
        assertEquals("Follower must receive D-pad in universal parity during Watch Party", followerDownCount + 1, follower.commandLog.count { it == "KEY:DOWN" })

        // 3. Switch to Direct Stream (HLS m3u8)
        val directJob = coordinator.beamMediaToTv(leader.toRemoteDevice(), "https://cinema.rmediatech.com/master.m3u8", allDevices, CinemaSource.DIRECT_URL)
        directJob?.join()
        testScheduler.runCurrent()
        assertEquals(CinemaSource.DIRECT_URL, coordinator.activeSource)

        // Assert beam command formatting is correct for direct stream
        val lastFollowerBeam = follower.commandLog.filter { it.startsWith("BEAM_URI:") }.last()
        assertTrue("Follower received direct stream URI", lastFollowerBeam.contains("master.m3u8"))

        coordinator.stopWatchParty(allDevices, leader.toRemoteDevice())
        testScheduler.runCurrent()

        // When stopped, D-pad no longer fans out
        val followerDownAfterStop = follower.commandLog.count { it == "KEY:DOWN" }
        coordinator.fanoutKeyIfWatchParty(RemoteKey.DOWN, leader.toRemoteDevice(), allDevices)
        testScheduler.runCurrent()
        assertEquals("Follower must NOT receive keys after Watch Party teardown", followerDownAfterStop, follower.commandLog.count { it == "KEY:DOWN" })
    }

    @Test
    fun testAsymmetricNetworkLag_FollowerJitterDoesNotBlockLeaderOrDropPlayback() = runTest {
        val app = Application()
        val registry = TvProtocolRegistry(context = null)
        val leader = VirtualTvHardwareNode("fast_leader", "Fast LAN Google TV", DeviceType.GOOGLE_TV, "192.168.1.186")
        val follower = VirtualTvHardwareNode("jitter_follower", "Jitter Wi-Fi Fire TV", DeviceType.FIRE_TV, "192.168.1.158")
        follower.networkLatencyMs = 250L // 250ms simulated network lag

        registry.register(leader)
        registry.register(follower)
        val allDevices = listOf(leader.toRemoteDevice(), follower.toRemoteDevice())
        val coordinator = ArgusMediaCoordinator(context = app, scope = this, registry = registry)

        coordinator.toggleWatchPartyDevice(follower.ip, allDevices, leaderIp = leader.ip)
        testScheduler.runCurrent()

        // Beam YouTube stream
        val job = coordinator.beamMediaToTv(leader.toRemoteDevice(), "https://www.youtube.com/watch?v=dQw4w9WgXcQ", allDevices, CinemaSource.YOUTUBE)
        job?.join()
        testScheduler.advanceTimeBy(300L)
        testScheduler.runCurrent()

        // Leader sends PLAY_PAUSE
        leader.sendKey(leader.toRemoteDevice(), RemoteKey.PLAY_PAUSE)
        coordinator.fanoutKeyIfWatchParty(RemoteKey.PLAY_PAUSE, leader.toRemoteDevice(), allDevices)
        testScheduler.advanceTimeBy(300L)
        testScheduler.runCurrent()

        assertEquals("Leader toggled to PAUSED", PlaybackStatus.PAUSED, leader.playbackStatus)
        assertEquals("Laggy follower successfully converged to PAUSED without dropping packet", PlaybackStatus.PAUSED, follower.playbackStatus)

        coordinator.stopWatchParty(allDevices)
        testScheduler.advanceTimeBy(300L)
        testScheduler.runCurrent()
    }

    @Test
    fun testFollowerTransientFrameDrop_RecoversGracefullyWithoutCrashingUI() = runTest {
        val app = Application()
        val registry = TvProtocolRegistry(context = null)
        val leader = VirtualTvHardwareNode("leader_tv", "Leader TV", DeviceType.GOOGLE_TV, "192.168.1.186")
        val follower = VirtualTvHardwareNode("drop_follower", "Dropping TV", DeviceType.FIRE_TV, "192.168.1.158")
        follower.failNextKeys = 1 // Simulates 1 dropped Wi-Fi frame

        registry.register(leader)
        registry.register(follower)
        val allDevices = listOf(leader.toRemoteDevice(), follower.toRemoteDevice())
        val coordinator = ArgusMediaCoordinator(context = app, scope = this, registry = registry)

        coordinator.toggleWatchPartyDevice(follower.ip, allDevices, leaderIp = leader.ip)
        testScheduler.runCurrent()

        // First volume key: follower fails with transient error, leader succeeds
        leader.sendKey(leader.toRemoteDevice(), RemoteKey.VOLUME_UP)
        coordinator.fanoutKeyIfWatchParty(RemoteKey.VOLUME_UP, leader.toRemoteDevice(), allDevices)
        testScheduler.runCurrent()

        assertEquals("Leader stepped up to 16", 16, leader.volumeLevel)
        assertEquals("Follower remained at 15 due to transient drop", 15, follower.volumeLevel)

        // Second volume key: connection is healed, follower receives key!
        leader.sendKey(leader.toRemoteDevice(), RemoteKey.VOLUME_UP)
        coordinator.fanoutKeyIfWatchParty(RemoteKey.VOLUME_UP, leader.toRemoteDevice(), allDevices)
        testScheduler.runCurrent()

        assertEquals("Leader stepped up to 17", 17, leader.volumeLevel)
        assertEquals("Follower stepped up to 16 on healed retry", 16, follower.volumeLevel)

        coordinator.stopWatchParty(allDevices)
        testScheduler.runCurrent()
    }

    @Test
    fun testLivingRoomPartyTeardown_DispatchesPauseAndHomeInUnison() = runTest {
        val app = Application()
        val registry = TvProtocolRegistry(context = null)
        val livingRoomGtv = VirtualTvHardwareNode("gtv", "Living Room TV", DeviceType.GOOGLE_TV, "192.168.1.186")
        val bedroomFtv = VirtualTvHardwareNode("ftv", "Bedroom TV", DeviceType.FIRE_TV, "192.168.1.158")
        registry.register(livingRoomGtv)
        registry.register(bedroomFtv)

        val allDevices = listOf(livingRoomGtv.toRemoteDevice(), bedroomFtv.toRemoteDevice())
        val coordinator = ArgusMediaCoordinator(context = app, scope = this, registry = registry)

        coordinator.toggleWatchPartyDevice(bedroomFtv.ip, allDevices, leaderIp = livingRoomGtv.ip)
        testScheduler.runCurrent()

        // Beam media
        val job = coordinator.beamMediaToTv(livingRoomGtv.toRemoteDevice(), "https://www.youtube.com/watch?v=dQw4w9WgXcQ", allDevices, CinemaSource.YOUTUBE)
        job?.join()
        testScheduler.runCurrent()

        // Terminate party
        coordinator.stopWatchParty(allDevices, livingRoomGtv.toRemoteDevice())
        testScheduler.runCurrent()

        // Assert 100% clean teardown
        assertTrue("Follower list cleared", coordinator.watchPartyIps.isEmpty())
        assertNull("Now playing telemetry reset", coordinator.nowPlaying)
        assertNull("Active source reset", coordinator.activeSource)
        assertFalse("Sleep guard released on Google TV", livingRoomGtv.sleepGuardAcquired)
        assertFalse("Sleep guard released on Fire TV", bedroomFtv.sleepGuardAcquired)
        assertEquals("Stay on setting pristine (0) on Google TV", 0, livingRoomGtv.stayOnWhilePluggedInSetting)
        assertEquals("Stay on setting pristine (0) on Fire TV", 0, bedroomFtv.stayOnWhilePluggedInSetting)

        // Verify explicit PAUSE + HOME dispatches
        assertTrue("Google TV received PAUSE", livingRoomGtv.commandLog.contains("KEY:PAUSE"))
        assertTrue("Google TV received HOME", livingRoomGtv.commandLog.contains("KEY:HOME"))
        assertTrue("Fire TV received PAUSE", bedroomFtv.commandLog.contains("KEY:PAUSE"))
        assertTrue("Fire TV received HOME", bedroomFtv.commandLog.contains("KEY:HOME"))
        assertEquals("Google TV returned to IDLE", PlaybackStatus.IDLE, livingRoomGtv.playbackStatus)
        assertEquals("Fire TV returned to IDLE", PlaybackStatus.IDLE, bedroomFtv.playbackStatus)
    }

    @Test
    fun testAutoHealingDriftSentinel_ClassifiesYellowAndRedZonesAccurately() {
        // Assert mathematical thresholds
        assertEquals(TvAutoHealingSentinel.HealingZone.GREEN, TvAutoHealingSentinel.classifyZone(0L))
        assertEquals(TvAutoHealingSentinel.HealingZone.GREEN, TvAutoHealingSentinel.classifyZone(250L))
        assertEquals(TvAutoHealingSentinel.HealingZone.GREEN, TvAutoHealingSentinel.classifyZone(499L))

        assertEquals(TvAutoHealingSentinel.HealingZone.YELLOW, TvAutoHealingSentinel.classifyZone(500L))
        assertEquals(TvAutoHealingSentinel.HealingZone.YELLOW, TvAutoHealingSentinel.classifyZone(1500L))
        assertEquals(TvAutoHealingSentinel.HealingZone.YELLOW, TvAutoHealingSentinel.classifyZone(2500L))

        assertEquals(TvAutoHealingSentinel.HealingZone.RED, TvAutoHealingSentinel.classifyZone(2501L))
        assertEquals(TvAutoHealingSentinel.HealingZone.RED, TvAutoHealingSentinel.classifyZone(15000L))
    }
}
