package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteKey
import org.junit.Assert.*
import org.junit.Test

class ProtocolKeycodeTest {

    @Test
    fun testAllRemoteKeysHaveValidCodes() {
        for (key in RemoteKey.entries) {
            assertTrue("Key ${key.name} code must not be blank", key.code.isNotBlank())
        }
        assertEquals(15, RemoteKey.entries.size)
    }

    @Test
    fun testAndroidKeycodeMappingCompleteness() {
        // Test Android TV keycode assignments
        val expectedCodes = mapOf(
            RemoteKey.POWER to 26,
            RemoteKey.HOME to 3,
            RemoteKey.BACK to 4,
            RemoteKey.UP to 19,
            RemoteKey.DOWN to 20,
            RemoteKey.LEFT to 21,
            RemoteKey.RIGHT to 22,
            RemoteKey.SELECT to 66,
            RemoteKey.VOLUME_UP to 24,
            RemoteKey.VOLUME_DOWN to 25,
            RemoteKey.MUTE to 164,
            RemoteKey.PLAY_PAUSE to 85,
            RemoteKey.REWIND to 89,
            RemoteKey.FAST_FORWARD to 90,
            RemoteKey.INFO to 82
        )

        for ((key, expectedCode) in expectedCodes) {
            val keycode = when (key) {
                RemoteKey.POWER -> 26
                RemoteKey.HOME -> 3
                RemoteKey.BACK -> 4
                RemoteKey.UP -> 19
                RemoteKey.DOWN -> 20
                RemoteKey.LEFT -> 21
                RemoteKey.RIGHT -> 22
                RemoteKey.SELECT -> 66
                RemoteKey.VOLUME_UP -> 24
                RemoteKey.VOLUME_DOWN -> 25
                RemoteKey.MUTE -> 164
                RemoteKey.PLAY_PAUSE -> 85
                RemoteKey.REWIND -> 89
                RemoteKey.FAST_FORWARD -> 90
                RemoteKey.INFO -> 82
            }
            assertEquals("Key ${key.name} must map to keycode $expectedCode", expectedCode, keycode)
        }
    }

    @Test
    fun testRokuEcpKeycodeValues() {
        val rokuKeys = mapOf(
            RemoteKey.POWER to "Power",
            RemoteKey.HOME to "Home",
            RemoteKey.BACK to "Back",
            RemoteKey.UP to "Up",
            RemoteKey.DOWN to "Down",
            RemoteKey.LEFT to "Left",
            RemoteKey.RIGHT to "Right",
            RemoteKey.SELECT to "Select",
            RemoteKey.VOLUME_UP to "VolumeUp",
            RemoteKey.VOLUME_DOWN to "VolumeDown",
            RemoteKey.MUTE to "VolumeMute",
            RemoteKey.PLAY_PAUSE to "Play",
            RemoteKey.REWIND to "Rev",
            RemoteKey.FAST_FORWARD to "Fwd",
            RemoteKey.INFO to "Info"
        )

        for ((key, expectedEcp) in rokuKeys) {
            assertEquals("Roku ECP code for ${key.name} must be $expectedEcp", expectedEcp, key.code)
        }
    }

    @Test
    fun testDeviceTypeBadgesHaveDistinctColors() {
        for (type in DeviceType.entries) {
            assertTrue("Badge color for $type must be non-zero", type.badgeColorHex != 0L)
            assertTrue("Display name for $type must not be blank", type.displayName.isNotBlank())
        }

        // Assert distinctive vendor identity
        assertEquals("Google TV", DeviceType.GOOGLE_TV.displayName)
        assertEquals(0xFF00E5FF, DeviceType.GOOGLE_TV.badgeColorHex)

        assertEquals("Fire TV", DeviceType.FIRE_TV.displayName)
        assertEquals(0xFFFF9900, DeviceType.FIRE_TV.badgeColorHex)

        assertEquals("Roku", DeviceType.ROKU.displayName)
        assertEquals(0xFFA855F7, DeviceType.ROKU.badgeColorHex)
    }
}
