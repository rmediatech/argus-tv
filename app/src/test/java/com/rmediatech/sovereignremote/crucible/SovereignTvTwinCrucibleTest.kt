package com.rmediatech.sovereignremote.crucible

import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.PlaybackStatus
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.protocols.RokuProtocol
import com.rmediatech.sovereignremote.protocols.SamsungProtocol
import com.rmediatech.sovereignremote.protocols.TvAutoHealingSentinel
import com.rmediatech.sovereignremote.protocols.TvMediaBridge
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Sovereign TV Crucible & Digital Twin Verification Suite:
 * One Job: Proves protocol discovery, command dispatch, playhead synchronization,
 * and drift healing against real, live-listening ephemeral TV network twins across multiple brands.
 * Guarantees zero socket leaks and zero lingering processes via AutoCloseable RAII.
 */
class SovereignTvTwinCrucibleTest {

    @Test
    fun testRokuHardwareTwin_ProbeAndSendKey() = runBlocking {
        // Spin up an ephemeral Roku hardware twin
        SovereignTvTwin(brand = SovereignTvTwin.TvBrand.ROKU, modelName = "TCL Sovereign Roku TV").use { twin ->
            val rokuProto = RokuProtocol()
            val device = RemoteDevice(
                id = "roku_test",
                name = twin.modelName,
                ipAddress = "127.0.0.1",
                port = twin.port,
                deviceType = DeviceType.ROKU,
                modelName = twin.modelName,
                isEthernet = false,
                macAddress = "00:11:22:33:44:55"
            )

            // 1. Send Play Key
            val result = rokuProto.sendKey(device, RemoteKey.PLAY_PAUSE)
            assertTrue("SendKey should succeed over live socket", result.isSuccess)
            assertEquals("Roku twin must capture Play/Pause keypress", 1, twin.receivedKeypresses.size)
            assertEquals("Play", twin.receivedKeypresses[0])
            assertEquals("Twin playback state should toggle to play", "play", twin.playbackStatus)

            // 2. Query Playback Telemetry
            twin.playheadMs = 45000L
            val telemetry = rokuProto.queryNowPlaying(device)
            assertNotNull("Telemetry must be returned by live twin", telemetry)
            assertEquals(PlaybackStatus.PLAYING, telemetry?.status)
            assertTrue("Playhead must match twin simulated position", (telemetry?.positionMs ?: 0L) >= 45000L)
        }
    }

    @Test
    fun testSamsungTizenTwin_ProbeRestDiscovery() = runBlocking {
        SovereignTvTwin(brand = SovereignTvTwin.TvBrand.SAMSUNG_TIZEN, modelName = "Samsung QN90D").use { twin ->
            val samsungProto = SamsungProtocol()
            val probed = samsungProto.probe("127.0.0.1", twin.port)
            assertNotNull("Samsung protocol must probe live REST API twin", probed)
            assertEquals("Samsung QN90D", probed?.modelName)
            assertEquals(DeviceType.SAMSUNG_TIZEN, probed?.deviceType)
        }
    }

    @Test
    fun testWatchPartyThreeTvDriftReconciliation() = runBlocking {
        // Spin up 3 simulated TVs simultaneously: Leader + 2 Followers
        SovereignTvTwin(brand = SovereignTvTwin.TvBrand.ROKU, modelName = "Living Room Roku TV").use { leaderTwin ->
            SovereignTvTwin(brand = SovereignTvTwin.TvBrand.ROKU, modelName = "Bedroom Roku TV").use { follower1Twin ->
                SovereignTvTwin(brand = SovereignTvTwin.TvBrand.ROKU, modelName = "Patio Roku TV").use { follower2Twin ->
                    
                    // Set up initial state: Leader at 100,000ms, Follower 1 drifting by 1,200ms (YELLOW zone), Follower 2 by 4,000ms (RED zone)
                    leaderTwin.playbackStatus = "play"
                    leaderTwin.playheadMs = 100000L

                    follower1Twin.playbackStatus = "play"
                    follower1Twin.playheadMs = 98800L // 1200ms behind leader

                    follower2Twin.playbackStatus = "play"
                    follower2Twin.playheadMs = 96000L // 4000ms behind leader

                    val rokuProto = RokuProtocol()
                    val leaderDev = RemoteDevice("lead", leaderTwin.modelName, "127.0.0.1", leaderTwin.port, DeviceType.ROKU, leaderTwin.modelName, false, null)
                    val follower1Dev = RemoteDevice("f1", follower1Twin.modelName, "127.0.0.1", follower1Twin.port, DeviceType.ROKU, follower1Twin.modelName, false, null)
                    val follower2Dev = RemoteDevice("f2", follower2Twin.modelName, "127.0.0.1", follower2Twin.port, DeviceType.ROKU, follower2Twin.modelName, false, null)

                    val leaderState = rokuProto.queryNowPlaying(leaderDev)
                    val follower1State = rokuProto.queryNowPlaying(follower1Dev)
                    val follower2State = rokuProto.queryNowPlaying(follower2Dev)

                    assertNotNull(leaderState)
                    assertNotNull(follower1State)
                    assertNotNull(follower2State)

                    // Evaluate Follower 1 (Yellow zone slipstream hold)
                    val decision1 = TvAutoHealingSentinel.evaluateDrift(leaderState, follower1State)
                    assertTrue("Follower 1 must trigger SlipstreamHold", decision1 is TvAutoHealingSentinel.AutoHealDecision.SlipstreamHold)

                    // Evaluate Follower 2 (Red zone hard reconciliation)
                    val decision2 = TvAutoHealingSentinel.evaluateDrift(leaderState, follower2State)
                    assertTrue("Follower 2 must trigger HardReconciliation", decision2 is TvAutoHealingSentinel.AutoHealDecision.HardReconciliation)
                    val hardRec = decision2 as TvAutoHealingSentinel.AutoHealDecision.HardReconciliation
                    assertTrue("Hard reconciliation drift must be approx 4000ms", kotlin.math.abs(hardRec.driftMs - 4000L) <= 50L)
                }
            }
        }
    }

    @Test
    fun testFireTvProfilePickerInterceptionDetection() {
        // Verify that the Profile Picker screen on Fire TV is properly unmasked as BUFFERING, never PLAYING
        val twin = SovereignTvTwin(
            brand = SovereignTvTwin.TvBrand.FIRE_TV,
            modelName = "Fire TV Stick 4K Max",
            personality = SovereignTvTwin.TvPersonality.ProfilePickerInterception()
        )
        twin.use {
            val dumpsysRaw = twin.generateAdbDumpsysOutput()
            val state = TvMediaBridge.parseAdbMediaSession(dumpsysRaw)

            assertNotNull("Adb media session parser must produce state", state)
            assertEquals("Profile picker screen must NEVER be categorized as PLAYING", PlaybackStatus.BUFFERING, state?.status)
            assertEquals("dev.cobalt.app", state?.appPackage)
            assertEquals(0L, state?.positionMs)
        }
    }

    @Test
    fun testTvAgnosticStopWatchPartyFactoryReset() = kotlinx.coroutines.test.runTest {
        // Spin up two disparate brand virtual TV nodes: Google TV and Fire TV
        val gtv = VirtualTvHardwareNode("gtv_node", "Hisense Google TV", DeviceType.GOOGLE_TV, "192.168.1.186")
        val ftv = VirtualTvHardwareNode("ftv_node", "Fire TV Stick 4K Max", DeviceType.FIRE_TV, "192.168.1.158")

        val registry = com.rmediatech.sovereignremote.protocols.TvProtocolRegistry(context = null)
        registry.register(gtv)
        registry.register(ftv)

        val coordinator = com.rmediatech.sovereignremote.state.ArgusMediaCoordinator(
            context = android.app.Application(),
            scope = this,
            registry = registry
        )

        val allDevs = listOf(gtv.toRemoteDevice(), ftv.toRemoteDevice())

        // 1. Engage Watch Party
        coordinator.toggleWatchPartyDevice(ftv.ip, allDevs)
        testScheduler.runCurrent()
        assertTrue("Fire TV must be marked as synced", coordinator.watchPartyIps.contains(ftv.ip))
        assertTrue("Fire TV must have acquired sleep guard", ftv.sleepGuardAcquired)

        // 2. Trigger the new STOP PARTY teardown
        coordinator.stopWatchParty(allDevs)
        testScheduler.runCurrent()

        // 3. Assert complete TV-agnostic cleanup
        assertTrue("Watch Party IPs must be completely empty", coordinator.watchPartyIps.isEmpty())
        assertFalse("Fire TV sleep guard must be cleanly released", ftv.sleepGuardAcquired)
        assertEquals("Fire TV stay-on setting must be factory default 0", 0, ftv.stayOnWhilePluggedInSetting)
        assertEquals("Google TV stay-on setting must be factory default 0", 0, gtv.stayOnWhilePluggedInSetting)
        assertNull("Healing snapshot must be cleared", coordinator.healingSnapshot)
        assertTrue("Follower telemetry must be cleared", coordinator.followerTelemetry.isEmpty())
    }

    @Test
    fun testWatchPartyPersistenceAndAutoRehydrationAcrossProcessDeath() = kotlinx.coroutines.test.runTest {
        val app = android.app.Application()
        val registry = com.rmediatech.sovereignremote.protocols.TvProtocolRegistry(context = null)
        val ftv = VirtualTvHardwareNode("ftv_node", "Fire TV Stick 4K Max", DeviceType.FIRE_TV, "192.168.1.158")
        registry.register(ftv)
        val allDevs = listOf(ftv.toRemoteDevice())

        // 1. Session 1: User engages Watch Party with Fire TV
        val coordinatorSession1 = com.rmediatech.sovereignremote.state.ArgusMediaCoordinator(
            context = app,
            scope = this,
            registry = registry
        )
        coordinatorSession1.toggleWatchPartyDevice(ftv.ip, allDevs, leaderIp = "192.168.1.186")
        testScheduler.runCurrent()
        assertTrue("Session 1 must have Fire TV in party", coordinatorSession1.watchPartyIps.contains(ftv.ip))

        // 2. Process Death Simulation: Argus is killed / closed while party is still playing on TVs!
        // Session 1 is dereferenced.

        // 3. Session 2: User re-launches Argus
        val coordinatorSession2 = com.rmediatech.sovereignremote.state.ArgusMediaCoordinator(
            context = app,
            scope = this,
            registry = registry
        )
        // Assert: Session 2 automatically rehydrates the active party cluster from persistent SST store!
        assertTrue("Session 2 MUST auto-rehydrate ongoing party followers", coordinatorSession2.watchPartyIps.contains(ftv.ip))

        // 4. User ends party from Session 2
        coordinatorSession2.stopWatchParty(allDevs)
        testScheduler.runCurrent()
        assertTrue("Party must be cleanly stopped from restored session", coordinatorSession2.watchPartyIps.isEmpty())

        // 5. Session 3: Next launch is completely clean
        val coordinatorSession3 = com.rmediatech.sovereignremote.state.ArgusMediaCoordinator(
            context = app,
            scope = this,
            registry = registry
        )
        assertTrue("Clean launch after stop must have empty party", coordinatorSession3.watchPartyIps.isEmpty())
    }
}
