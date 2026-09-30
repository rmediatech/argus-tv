package com.rmediatech.sovereignremote.model

import org.junit.Assert.*
import org.junit.Test

class DeviceSerializerTest {

    @Test
    fun testSerializationRoundtrip() {
        val originalDevices = listOf(
            RemoteDevice(
                id = "googletv_001",
                name = "Rick's TV (Hisense)",
                ipAddress = "192.168.1.151",
                port = 8008,
                deviceType = DeviceType.GOOGLE_TV,
                modelName = "Hisense Google TV",
                isEthernet = true,
                macAddress = "88:b8:63:08:df:00"
            ),
            RemoteDevice(
                id = "firetv_002",
                name = "Fire TV Stick 4K Max",
                ipAddress = "192.168.1.158",
                port = 5555,
                deviceType = DeviceType.FIRE_TV,
                modelName = "Fire TV Stick 4K Max",
                isEthernet = false,
                macAddress = "68:f6:3b:b4:5c:14"
            )
        )

        val json = DeviceSerializer.serialize(originalDevices)
        val deserialized = DeviceSerializer.deserialize(json)

        assertEquals(2, deserialized.size)
        assertEquals(originalDevices[0].ipAddress, deserialized[0].ipAddress)
        assertEquals(originalDevices[0].macAddress, deserialized[0].macAddress)
        assertEquals(originalDevices[0].deviceType, deserialized[0].deviceType)
        assertEquals(originalDevices[0].isEthernet, deserialized[0].isEthernet)

        assertEquals(originalDevices[1].ipAddress, deserialized[1].ipAddress)
        assertEquals(originalDevices[1].macAddress, deserialized[1].macAddress)
    }

    @Test
    fun testBackwardsCompatibilityWithoutMacAddress() {
        // Legacy JSON without macAddress key
        val legacyJson = """[{"id":"tv_old","name":"Living Room TV","ipAddress":"192.168.1.120","port":8008,"deviceType":"GOOGLE_TV"}]"""
        val devices = DeviceSerializer.deserialize(legacyJson)

        assertEquals(1, devices.size)
        assertEquals("192.168.1.120", devices[0].ipAddress)
        assertNull("Missing macAddress in legacy JSON should deserialize as null without crashing", devices[0].macAddress)
    }

    @Test
    fun testMergeInventoryPreservesMacAddress() {
        val cached = listOf(
            RemoteDevice(
                id = "tv_1",
                name = "Rick's TV",
                ipAddress = "192.168.1.151",
                port = 8008,
                deviceType = DeviceType.GOOGLE_TV,
                macAddress = "88:b8:63:08:df:00"
            )
        )

        // Fresh probe where macAddress is missing/null (e.g. TV was starting up)
        val fresh = listOf(
            RemoteDevice(
                id = "tv_1",
                name = "Google TV (192.168.1.151)",
                ipAddress = "192.168.1.151",
                port = 8008,
                deviceType = DeviceType.GOOGLE_TV,
                macAddress = null
            )
        )

        val merged = DeviceSerializer.merge(cached, fresh)
        assertEquals(1, merged.size)
        assertEquals("88:b8:63:08:df:00", merged[0].macAddress)
        assertEquals("Rick's TV", merged[0].name)
    }

    @Test
    fun testAdversarialFormulaAndAnsiSanitization() {
        val dangerousDevices = listOf(
            RemoteDevice(
                id = "dev_inject",
                name = "=cmd|' /C calc'!A0",       // Spreadsheet formula injection
                ipAddress = "192.168.1.200",
                port = 8008,
                deviceType = DeviceType.GENERIC,
                modelName = "\u001b[31;1mRedAlert\u001b[0m" // ANSI escape poisoning
            )
        )

        val json = DeviceSerializer.serialize(dangerousDevices)
        val deserialized = DeviceSerializer.deserialize(json)

        assertEquals(1, deserialized.size)
        assertTrue("Formula payload must be neutralized with leading quote", deserialized[0].name.startsWith("'="))
        assertFalse("ANSI escape sequence must be scrubbed", deserialized[0].modelName!!.contains("\u001b["))
        assertEquals("RedAlert", deserialized[0].modelName)
    }

    @Test
    fun testMalformedJsonResilience() {
        val malformedCases = listOf(
            null,
            "",
            "   ",
            "not a json",
            "{ \"object\": \"not an array\" }",
            "[ { \"name\": \"No IP\" } ]",
            "[ { \"ipAddress\": \"192.168.1.50\", \"deviceType\": \"NON_EXISTENT_VENDOR\" } ]"
        )

        for (case in malformedCases) {
            val result = DeviceSerializer.deserialize(case)
            assertNotNull(result) // Must never return null or crash
        }

        // Test that unknown deviceType safely defaults to GENERIC
        val unknownTypeJson = """[{"id":"x","name":"TV","ipAddress":"192.168.1.50","port":8008,"deviceType":"FUTURE_ALIEN_TV"}]"""
        val result = DeviceSerializer.deserialize(unknownTypeJson)
        assertEquals(1, result.size)
        assertEquals(DeviceType.GENERIC, result[0].deviceType)
    }
}
