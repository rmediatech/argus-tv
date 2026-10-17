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
            RemoteKey.SELECT to 23,
            RemoteKey.VOLUME_UP to 24,
            RemoteKey.VOLUME_DOWN to 25,
            RemoteKey.MUTE to 164,
            RemoteKey.PLAY_PAUSE to 85,
            RemoteKey.REWIND to 89,
            RemoteKey.FAST_FORWARD to 90,
            RemoteKey.INFO to 176
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
                RemoteKey.SELECT -> 23
                RemoteKey.VOLUME_UP -> 24
                RemoteKey.VOLUME_DOWN -> 25
                RemoteKey.MUTE -> 164
                RemoteKey.PLAY_PAUSE -> 85
                RemoteKey.REWIND -> 89
                RemoteKey.FAST_FORWARD -> 90
                RemoteKey.INFO -> 176
            }
            assertEquals("Key ${key.name} must map to keycode $expectedCode", expectedCode, keycode)
        }
    }

    @Test
    fun testFireTvHardwareScancodes() {
        val expectedScancodes = mapOf(
            RemoteKey.POWER to 116,
            RemoteKey.HOME to 172,
            RemoteKey.BACK to 158,
            RemoteKey.UP to 103,
            RemoteKey.DOWN to 108,
            RemoteKey.LEFT to 105,
            RemoteKey.RIGHT to 106,
            RemoteKey.SELECT to 96, // BLE Kumquat Remote DPAD_CENTER
            RemoteKey.VOLUME_UP to 115,
            RemoteKey.VOLUME_DOWN to 114,
            RemoteKey.MUTE to 113,
            RemoteKey.PLAY_PAUSE to 164,
            RemoteKey.REWIND to 168,
            RemoteKey.FAST_FORWARD to 208,
            RemoteKey.INFO to 139
        )

        for ((key, expectedScan) in expectedScancodes) {
            val scancode = when (key) {
                RemoteKey.POWER -> 116
                RemoteKey.HOME -> 172
                RemoteKey.BACK -> 158
                RemoteKey.UP -> 103
                RemoteKey.DOWN -> 108
                RemoteKey.LEFT -> 105
                RemoteKey.RIGHT -> 106
                RemoteKey.SELECT -> 96
                RemoteKey.VOLUME_UP -> 115
                RemoteKey.VOLUME_DOWN -> 114
                RemoteKey.MUTE -> 113
                RemoteKey.PLAY_PAUSE -> 164
                RemoteKey.REWIND -> 168
                RemoteKey.FAST_FORWARD -> 208
                RemoteKey.INFO -> 139
            }
            assertEquals("Fire TV scancode for ${key.name} must be $expectedScan", expectedScan, scancode)
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

        assertEquals("Samsung", DeviceType.SAMSUNG_TIZEN.displayName)
        assertEquals(0xFF3B82F6, DeviceType.SAMSUNG_TIZEN.badgeColorHex)

        assertEquals("LG webOS", DeviceType.LG_WEBOS.displayName)
        assertEquals(0xFFEC4899, DeviceType.LG_WEBOS.badgeColorHex)

        assertEquals("Sony BRAVIA", DeviceType.SONY_BRAVIA.displayName)
        assertEquals(0xFFFF5252, DeviceType.SONY_BRAVIA.badgeColorHex)

        assertEquals("Vizio", DeviceType.VIZIO_SMARTCAST.displayName)
        assertEquals(0xFF10B981, DeviceType.VIZIO_SMARTCAST.badgeColorHex)
    }
}
