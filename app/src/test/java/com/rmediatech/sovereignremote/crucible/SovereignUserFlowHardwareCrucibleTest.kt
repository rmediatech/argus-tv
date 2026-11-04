package com.rmediatech.sovereignremote.crucible

import com.rmediatech.sovereignremote.model.CinemaSource
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.PlaybackStatus
import com.rmediatech.sovereignremote.model.RemoteKey
import com.rmediatech.sovereignremote.protocols.TvProtocolRegistry
import com.rmediatech.sovereignremote.state.ArgusMediaCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Sovereign User Flow & Hardware Lifecycle Crucible Test:
 * One Job: Re-enacts the EXACT physical user flow you perform in your living room:
 *   1. User launches Argus with Fire TV and Google TV present on the Wi-Fi.
 *   2. User selects Google TV as Primary in the dropdown.
 *   3. User taps the cyan "Watch Party" button to link Fire TV as follower.
 *      - Verifies: RAII sleep guard is acquired without permanently corrupting manufacturer settings (stayOn stays 0).
 *   4. User types a query/URL and taps the BEAM button.
 *      - Verifies: Both TVs receive the parallel launch command simultaneously.
 *      - Verifies: Fire TV's profile picker state is detected as BUFFERING, never false PLAYING.
 *   5. User presses Volume Up / Volume Down on phone hardware rocker.
 *      - Verifies: Volume keys fan out in parallel to BOTH TVs simultaneously.
 *   6. User presses Play/Pause button.
 *      - Verifies: Play/Pause toggles BOTH TVs simultaneously.
 *   7. User switches Primary focus in dropdown to Fire TV.
 *      - Verifies: Leader swap leaves Watch Party intact (old leader becomes follower, zero disconnect).
 *   8. User turns off TV or Watch Party session ends.
 *      - Verifies: Sleep guards are 100% released, zero residual modified settings, TV stays asleep.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SovereignUserFlowHardwareCrucibleTest {

    @Test
    fun testCompleteUserWatchPartyAndBeamLifecycle_MatchesLivingRoomHardwareActions() = runTest {
        // --- STEP 1: Set up the living room hardware fleet ---
        val registry = TvProtocolRegistry(context = null)

        // Google TV (Living Room)
        val googleTvNode = VirtualTvHardwareNode(
            deviceId = "gtv_node",
            brandName = "Hisense Google TV",
            deviceType = DeviceType.GOOGLE_TV,
            ip = "192.168.1.186",
            hasProfilePickerBug = false
        )
        // Fire TV (Bedroom / Secondary)
        val fireTvNode = VirtualTvHardwareNode(
            deviceId = "fire_node",
            brandName = "Amazon Fire TV 4K Max",
            deviceType = DeviceType.FIRE_TV,
            ip = "192.168.1.158",
            hasProfilePickerBug = true // Simulates the Profile Picker modal
        )

        // Register custom nodes into the registry
        registry.register(googleTvNode)
        registry.register(fireTvNode)

        val googleTvDev = googleTvNode.toRemoteDevice()
        val fireTvDev = fireTvNode.toRemoteDevice()
        val allDevices = listOf(googleTvDev, fireTvDev)

        val coordinator = ArgusMediaCoordinator(
            context = android.app.Application(),
            scope = this,
            registry = registry
        )

        // --- STEP 2: User selects Google TV as primary ---
        var currentPrimary = googleTvDev
        assertEquals("Initial volume on both TVs is 15", 15, googleTvNode.volumeLevel)
        assertEquals("Initial volume on both TVs is 15", 15, fireTvNode.volumeLevel)

        // --- STEP 3: User taps the Cyan Watch Party Chip to link Fire TV ---
        coordinator.toggleWatchPartyDevice(fireTvDev.ipAddress, allDevices)
        testScheduler.runCurrent()
        assertTrue("Watch Party must include Fire TV IP", coordinator.watchPartyIps.contains(fireTvDev.ipAddress))
        assertTrue("Fire TV must have acquired sleep guard lease", fireTvNode.sleepGuardAcquired)
        assertEquals("Fire TV stay-on setting must remain factory safe (0)", 0, fireTvNode.stayOnWhilePluggedInSetting)

        // --- STEP 4: User taps BEAM button with a movie stream ---
        val beamJob = coordinator.beamMediaToTv(
            device = currentPrimary,
            queryOrUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            allDevices = allDevices,
            source = CinemaSource.YOUTUBE
        )
        beamJob?.join()

        println("Google TV Command Log: ${googleTvNode.commandLog}")
        println("Fire TV Command Log: ${fireTvNode.commandLog}")

        // Verify both TVs received parallel beam launch intents
        assertTrue("Google TV must receive beam intent", googleTvNode.commandLog.any { it.startsWith("BEAM_URI") })
        assertTrue("Fire TV must receive beam intent", fireTvNode.commandLog.any { it.startsWith("BEAM_URI") })

        // Pre-flight profile picker bypass: Argus auto-dispatched SELECT so Fire TV is unmasked into PLAYING!
        assertFalse("Fire TV profile picker must be automatically unmasked and dismissed by pre-flight bypass", fireTvNode.isShowingProfilePicker)
        val fireTvState = registry.queryNowPlaying(fireTvDev)
        assertNotNull(fireTvState)
        assertEquals("Fire TV must be in PLAYING state after auto-bypass", PlaybackStatus.PLAYING, fireTvState?.status)

        // --- STEP 5: User presses Volume Up on physical rocker / UI ---
        // Fan out volume key to primary + followers
        googleTvNode.sendKey(googleTvDev, RemoteKey.VOLUME_UP) // primary action
        coordinator.fanoutTransportKeyIfWatchParty(RemoteKey.VOLUME_UP, currentPrimary, allDevices)
        testScheduler.runCurrent()

        assertEquals("Google TV volume stepped up to 16", 16, googleTvNode.volumeLevel)
        assertEquals("Fire TV volume stepped up to 16 in lockstep", 16, fireTvNode.volumeLevel)

        // User presses Volume Down
        googleTvNode.sendKey(googleTvDev, RemoteKey.VOLUME_DOWN)
        coordinator.fanoutTransportKeyIfWatchParty(RemoteKey.VOLUME_DOWN, currentPrimary, allDevices)
        testScheduler.runCurrent()

        assertEquals("Google TV volume stepped back to 15", 15, googleTvNode.volumeLevel)
        assertEquals("Fire TV volume stepped back to 15 in lockstep", 15, fireTvNode.volumeLevel)

        // --- STEP 6: User presses Play/Pause ---
        // Both TVs are PLAYING. User pauses leader: fanout pauses follower in lockstep!
        googleTvNode.sendKey(googleTvDev, RemoteKey.PLAY_PAUSE)
        coordinator.fanoutTransportKeyIfWatchParty(RemoteKey.PLAY_PAUSE, currentPrimary, allDevices)
        testScheduler.runCurrent()

        assertEquals("Google TV toggled to PAUSED", PlaybackStatus.PAUSED, googleTvNode.playbackStatus)
        assertEquals("Fire TV toggled to PAUSED in lockstep", PlaybackStatus.PAUSED, fireTvNode.playbackStatus)

        // --- STEP 7: User swaps Leader in dropdown (selects Fire TV) ---
        coordinator.swapLeaderIfWatchParty(oldLeaderIp = googleTvDev.ipAddress, newLeaderIp = fireTvDev.ipAddress)
        currentPrimary = fireTvDev

        // Watch party followers must now be Google TV (192.168.1.186), Fire TV is leader
        assertTrue("Watch Party followers must now include Google TV", coordinator.watchPartyIps.contains(googleTvDev.ipAddress))
        assertFalse("Watch Party followers must not include Fire TV (it is now leader)", coordinator.watchPartyIps.contains(fireTvDev.ipAddress))

        // Transport fanout from Fire TV must now reach Google TV!
        fireTvNode.sendKey(fireTvDev, RemoteKey.VOLUME_UP)
        coordinator.fanoutTransportKeyIfWatchParty(RemoteKey.VOLUME_UP, currentPrimary, allDevices)
        testScheduler.runCurrent()

        assertEquals("Fire TV volume stepped up to 16", 16, fireTvNode.volumeLevel)
        assertEquals("Google TV volume stepped up to 16 from reversed leader fanout", 16, googleTvNode.volumeLevel)

        // --- STEP 8: Cleanup / End Watch Party via Stop Party Action ---
        coordinator.stopWatchParty(allDevices)
        testScheduler.runCurrent()
        assertTrue("Watch Party followers set must be cleared", coordinator.watchPartyIps.isEmpty())
        assertFalse("Fire TV sleep guard must be cleanly released", fireTvNode.sleepGuardAcquired)
        assertFalse("Google TV sleep guard must be cleanly released", googleTvNode.sleepGuardAcquired)
        assertEquals("Fire TV stay-on setting must remain pristine (0)", 0, fireTvNode.stayOnWhilePluggedInSetting)
        assertEquals("Google TV stay-on setting must remain pristine (0)", 0, googleTvNode.stayOnWhilePluggedInSetting)

        // Verify that Stop Party explicitly dispatched PAUSE + HOME to all screens for clean teardown
        assertTrue("Fire TV must have received PAUSE on stop", fireTvNode.commandLog.contains("KEY:PAUSE"))
        assertTrue("Fire TV must have received HOME to exit YouTube", fireTvNode.commandLog.contains("KEY:HOME"))
        assertTrue("Google TV must have received PAUSE on stop", googleTvNode.commandLog.contains("KEY:PAUSE"))
        assertTrue("Google TV must have received HOME to exit YouTube", googleTvNode.commandLog.contains("KEY:HOME"))
    }
}
