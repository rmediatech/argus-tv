package com.rmediatech.sovereignremote.discovery

import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.protocols.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Universal Sovereign Adversarial TDD++++ Test Battery for SmartTvClassifier:
 * Tests formula injection neutralization, ANSI escape stripping, massive buffer bombs,
 * and deterministic multi-protocol resolution.
 */
class SmartTvClassifierAdversarialTest {

    private lateinit var registry: TvProtocolRegistry

    @Before
    fun setUp() {
        registry = TvProtocolRegistry(null)
    }

    @Test
    fun testFormulaInjectionNeutralizedInSanitizeDevice() {
        val dangerousNames = listOf(
            "=cmd|' /C calc'!A0",
            "+1234567890",
            "-2+3*cmd",
            "@SUM(1,2)",
            "\tTAB_INJECT"
        )

        for (name in dangerousNames) {
            val dev = RemoteDevice(
                id = "dev_1",
                name = name,
                ipAddress = "192.168.1.100",
                port = 8001,
                deviceType = DeviceType.SAMSUNG_TIZEN,
                modelName = name
            )

            val sanitized = SmartTvClassifier.sanitizeDevice(dev)
            if (name.startsWith("=") || name.startsWith("+") || name.startsWith("-") || name.startsWith("@")) {
                assertTrue("Formula injection must start with single quote: ${sanitized.name}", sanitized.name.startsWith("'"))
                assertTrue("Model must also be sanitized: ${sanitized.modelName}", sanitized.modelName?.startsWith("'") == true)
            }
        }
    }

    @Test
    fun testAnsiEscapeSequencesScrubbed() {
        val poisonedName = "\u001B[31;1mHostile TV\u001B[0m"
        val dev = RemoteDevice(
            id = "dev_ansi",
            name = poisonedName,
            ipAddress = "192.168.1.101",
            port = 3000,
            deviceType = DeviceType.LG_WEBOS,
            modelName = poisonedName
        )

        val sanitized = SmartTvClassifier.sanitizeDevice(dev)
        assertFalse("ANSI escape codes must be stripped", sanitized.name.contains("\u001B["))
        assertEquals("Hostile TV", sanitized.name)
    }

    @Test
    fun testMassiveBufferAllocationBombBounded() {
        val hugeString = "A".repeat(50_000)
        val dev = RemoteDevice(
            id = "bomb_id",
            name = hugeString,
            ipAddress = "192.168.1.102",
            port = 8060,
            deviceType = DeviceType.ROKU,
            modelName = hugeString
        )

        val sanitized = SmartTvClassifier.sanitizeDevice(dev)
        assertTrue("Name length must be bounded to 64 chars", sanitized.name.length <= 64)
        assertTrue("Model length must be bounded to 64 chars", (sanitized.modelName?.length ?: 0) <= 64)
    }

    @Test
    fun testEmptyOrTrailingUnderscoreIdRepaired() {
        val dev = RemoteDevice(
            id = "googletv_",
            name = "Valid TV",
            ipAddress = "192.168.1.103",
            port = 8008,
            deviceType = DeviceType.GOOGLE_TV
        )

        val sanitized = SmartTvClassifier.sanitizeDevice(dev)
        assertFalse("ID must not end with trailing underscore", sanitized.id.endsWith("_"))
        assertEquals("google_tv_192.168.1.103", sanitized.id)
    }

    @Test
    fun testClassifyFromSsdpSamsungTizen() = runBlocking {
        val payload = """
            HTTP/1.1 200 OK
            LOCATION: http://192.168.1.110:8001/ms/1.0/
            SERVER: SHP, UPnP/1.0, Samsung UPnP SDK/1.0
            ST: urn:samsung.com:device:RemoteControlReceiver:1
            USN: uuid:samsung-tv-uuid::urn:samsung.com:device:RemoteControlReceiver:1
        """.trimIndent()

        val matched = registry.matchSsdp(payload)
        assertNotNull("Must match Samsung protocol", matched)
        assertEquals(DeviceType.SAMSUNG_TIZEN, matched?.deviceType)
    }

    @Test
    fun testClassifyFromSsdpLgWebOs() = runBlocking {
        val payload = """
            HTTP/1.1 200 OK
            LOCATION: http://192.168.1.111:13761/
            SERVER: Linux/3.10.19, UPnP/1.0, Portable SDK for UPnP devices/1.6.19
            ST: urn:lge-com:service:webos-second-screen:1
            USN: uuid:lg-webos-uuid::urn:lge-com:service:webos-second-screen:1
        """.trimIndent()

        val matched = registry.matchSsdp(payload)
        assertNotNull("Must match LG webOS protocol", matched)
        assertEquals(DeviceType.LG_WEBOS, matched?.deviceType)
    }

    @Test
    fun testClassifyFromSsdpSonyBravia() = runBlocking {
        val payload = """
            HTTP/1.1 200 OK
            LOCATION: http://192.168.1.112:20060/dial/dd.xml
            SERVER: Sony-Bravia/1.0 UPnP/1.0
            ST: urn:schemas-sony-com:service:IRCC:1
        """.trimIndent()

        val matched = registry.matchSsdp(payload)
        assertNotNull("Must match Sony BRAVIA protocol", matched)
        assertEquals(DeviceType.SONY_BRAVIA, matched?.deviceType)
    }

    @Test
    fun testClassifyFromSsdpVizioSmartCast() = runBlocking {
        val payload = """
            HTTP/1.1 200 OK
            LOCATION: https://192.168.1.113:7345/
            SERVER: Linux/4.4.3, UPnP/1.0, Vizio SmartCast/1.0
            ST: urn:schemas-vizio-com:device:smartcast:1
        """.trimIndent()

        val matched = registry.matchSsdp(payload)
        assertNotNull("Must match Vizio SmartCast protocol", matched)
        assertEquals(DeviceType.VIZIO_SMARTCAST, matched?.deviceType)
    }

    @Test
    fun testAllMajorProtocolsRegisteredInRegistry() {
        val expectedTypes = listOf(
            DeviceType.ROKU,
            DeviceType.SAMSUNG_TIZEN,
            DeviceType.LG_WEBOS,
            DeviceType.SONY_BRAVIA,
            DeviceType.VIZIO_SMARTCAST,
            DeviceType.GOOGLE_TV,
            DeviceType.FIRE_TV,
            DeviceType.GENERIC
        )

        for (type in expectedTypes) {
            val proto = registry.get(type)
            assertNotNull("Registry must provide a handler for $type", proto)
        }
    }

    @Test
    fun testInferBrandFromMacKnownVendors() {
        // Rick's Fire TV Stick 4K Max MAC
        assertEquals(DeviceType.FIRE_TV, SmartTvClassifier.inferBrandFromMac("68:F6:3B:B4:5C:14"))
        assertEquals(DeviceType.FIRE_TV, SmartTvClassifier.inferBrandFromMac("68-F6-3B-B4-5C-14"))
        assertEquals(DeviceType.FIRE_TV, SmartTvClassifier.inferBrandFromMac("68f63bb45c14"))

        // Roku, Samsung, LG, Sony, Vizio
        assertEquals(DeviceType.ROKU, SmartTvClassifier.inferBrandFromMac("b0:a7:37:aa:bb:cc"))
        assertEquals(DeviceType.SAMSUNG_TIZEN, SmartTvClassifier.inferBrandFromMac("e4:7c:f9:11:22:33"))
        assertEquals(DeviceType.LG_WEBOS, SmartTvClassifier.inferBrandFromMac("10:f9:20:44:55:66"))
        assertEquals(DeviceType.SONY_BRAVIA, SmartTvClassifier.inferBrandFromMac("00:13:a9:77:88:99"))
        assertEquals(DeviceType.VIZIO_SMARTCAST, SmartTvClassifier.inferBrandFromMac("00:19:fb:00:11:22"))
    }

    @Test
    fun testInferBrandFromMacAdversarialAndCorruptedInputs() {
        assertNull("Null MAC must return null safely", SmartTvClassifier.inferBrandFromMac(null))
        assertNull("Empty MAC must return null safely", SmartTvClassifier.inferBrandFromMac(""))
        assertNull("Whitespace MAC must return null safely", SmartTvClassifier.inferBrandFromMac("   "))
        assertNull("Too short MAC must return null safely", SmartTvClassifier.inferBrandFromMac("68:F6"))
        assertNull("Unknown MAC must return null", SmartTvClassifier.inferBrandFromMac("02:00:00:00:00:00"))
        assertNull("SQL injection payload must return null without panic", SmartTvClassifier.inferBrandFromMac("' OR 1=1;--"))
        assertNull("ANSI escape sequence must return null without panic", SmartTvClassifier.inferBrandFromMac("\u001B[31;1m68:F6:3B\u001B[0m"))
    }
}
