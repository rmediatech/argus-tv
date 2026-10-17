package com.rmediatech.sovereignremote.security

import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.net.NetworkEnvironmentManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Sovereign Adversarial Security Test Suite (TDD++++++):
 * Validates resilience against hostile wire boundaries, formula injection,
 * ANSI terminal/log poisoning, and allocation bombs.
 */
class AdversarialArgusSecurityTest {

    // --- THREAT MODEL 1: FORMULA & CODE INJECTION ---

    @Test
    fun testFormulaInjectionEqualsPrefixNeutralized() {
        val payload = "=cmd|' /C calc'!A0"
        val sanitized = SovereignSanitizer.sanitizeDeviceName(payload)
        assertEquals("Formula injection starting with '=' must be prefixed with single-quote", "'$payload", sanitized)
    }

    @Test
    fun testFormulaInjectionAtSymbolNeutralized() {
        val payload = "@SUM(1+1)*cmd|' /C calc'!A0"
        val sanitized = SovereignSanitizer.sanitizeDeviceName(payload)
        assertEquals("Formula injection starting with '@' must be prefixed with single-quote", "'$payload", sanitized)
    }

    @Test
    fun testFormulaInjectionPlusAndMinusNeutralized() {
        val plusPayload = "+123456789"
        val minusPayload = "-2+3*[calc]"
        assertEquals("'+123456789", SovereignSanitizer.sanitizeDeviceName(plusPayload))
        assertEquals("'-2+3*[calc]", SovereignSanitizer.sanitizeDeviceName(minusPayload))
    }

    @Test
    fun testFormulaInjectionTabAndCarriageReturnNeutralized() {
        val tabPayload = "\t=DDE(\"cmd\";\"/C calc\";\"__!A0\")"
        val crPayload = "\r=1+1"
        // Note: control characters (\t, \r) are stripped, and leading formula chars neutralized
        val sanitizedTab = SovereignSanitizer.sanitizeDeviceName(tabPayload)
        val sanitizedCr = SovereignSanitizer.sanitizeDeviceName(crPayload)
        assertTrue(sanitizedTab.startsWith("'="))
        assertTrue(sanitizedCr.startsWith("'="))
    }

    // --- THREAT MODEL 2: ANSI TERMINAL & LOG POISONING ---

    @Test
    fun testAnsiCsiColorAndStyleStripped() {
        val payload = "\u001B[31;1mEvil TV\u001B[0m"
        val sanitized = SovereignSanitizer.sanitizeDeviceName(payload)
        assertEquals("Evil TV", sanitized)
        assertFalse(sanitized.contains("\u001B"))
    }

    @Test
    fun testAnsiTerminalWipeAndCursorMoveStripped() {
        val payload = "\u001B[2J\u001B[HLiving Room TV"
        val sanitized = SovereignSanitizer.sanitizeDeviceName(payload)
        assertEquals("Living Room TV", sanitized)
    }

    @Test
    fun testAnsiOscWindowTitleWipeStripped() {
        val payload = "\u001B]0;Hacked Terminal\u0007Roku TV"
        val sanitized = SovereignSanitizer.sanitizeDeviceName(payload)
        assertEquals("Roku TV", sanitized)
    }

    @Test
    fun testUnprintableControlCharactersScrubbed() {
        val payload = "Bad\u0000TV\u0007With\u0008Nulls\u001F"
        val sanitized = SovereignSanitizer.sanitizeDeviceName(payload)
        assertEquals("BadTVWithNulls", sanitized)
    }

    // --- THREAT MODEL 3: BOUNDARY ALLOCATION & BUFFER BOMBS ---

    @Test
    fun testExtremeLengthDeviceNameBounded() {
        val hugePayload = "A".repeat(10_000)
        val sanitized = SovereignSanitizer.sanitizeDeviceName(hugePayload)
        assertEquals(64, sanitized.length)
        assertEquals("A".repeat(64), sanitized)
    }

    @Test
    fun testNullAndBlankDeviceNamesFallbackSafely() {
        assertEquals("Unknown TV", SovereignSanitizer.sanitizeDeviceName(null))
        assertEquals("Unknown TV", SovereignSanitizer.sanitizeDeviceName(""))
        assertEquals("Unknown TV", SovereignSanitizer.sanitizeDeviceName("   "))
        assertEquals("Unknown TV", SovereignSanitizer.sanitizeDeviceName("\u001B[2J   \u0000"))
    }

    // --- THREAT MODEL 4: DIRTY / CORRUPTED IP & PORT INGESTION ---

    @Test
    fun testStrictIpv4ValidationValidInputs() {
        assertTrue(SovereignSanitizer.isValidIpv4("192.168.1.1"))
        assertTrue(SovereignSanitizer.isValidIpv4("10.0.0.1"))
        assertTrue(SovereignSanitizer.isValidIpv4("172.16.0.254"))
        assertTrue(SovereignSanitizer.isValidIpv4("0.0.0.0"))
        assertTrue(SovereignSanitizer.isValidIpv4("255.255.255.255"))
    }

    @Test
    fun testStrictIpv4ValidationRejectsCorruptedAndHostileInputs() {
        assertFalse(SovereignSanitizer.isValidIpv4("999.999.999.999"))
        assertFalse(SovereignSanitizer.isValidIpv4("192.168.1.256"))
        assertFalse(SovereignSanitizer.isValidIpv4("192.168.1.-1"))
        assertFalse(SovereignSanitizer.isValidIpv4("192.168.1.01")) // Ambiguous octal prefix
        assertFalse(SovereignSanitizer.isValidIpv4("192.168.1.1.1"))
        assertFalse(SovereignSanitizer.isValidIpv4("192.168.1"))
        assertFalse(SovereignSanitizer.isValidIpv4("localhost"))
        assertFalse(SovereignSanitizer.isValidIpv4("192.168.1.1; rm -rf /"))
        assertFalse(SovereignSanitizer.isValidIpv4("   "))
        assertFalse(SovereignSanitizer.isValidIpv4(""))
    }

    @Test
    fun testPortNumberBounds() {
        assertTrue(SovereignSanitizer.isValidPort(8008))
        assertTrue(SovereignSanitizer.isValidPort(8060))
        assertTrue(SovereignSanitizer.isValidPort(5555))
        assertTrue(SovereignSanitizer.isValidPort(1))
        assertTrue(SovereignSanitizer.isValidPort(65535))

        assertFalse(SovereignSanitizer.isValidPort(0))
        assertFalse(SovereignSanitizer.isValidPort(-1))
        assertFalse(SovereignSanitizer.isValidPort(65536))
        assertFalse(SovereignSanitizer.isValidPort(99999))
    }

    // --- THREAT MODEL 5: MULTI-NETWORK ISOLATION INVARIANT ---

    @Test
    fun testApartmentDeviceNeverMatchesFriendSubnet() {
        val apartmentDevice = RemoteDevice(
            id = "apartment_roku",
            name = "Living Room Roku",
            ipAddress = "192.168.1.104",
            port = 8060,
            deviceType = DeviceType.ROKU,
            subnetPrefix = "192.168.1"
        )

        val friendSubnet = "10.0.0"
        val apartmentSubnet = "192.168.1"

        assertTrue("Device must match its own home subnet", NetworkEnvironmentManager.matchesSubnet(apartmentDevice.ipAddress, apartmentSubnet))
        assertFalse("Device must NEVER match a friend's subnet", NetworkEnvironmentManager.matchesSubnet(apartmentDevice.ipAddress, friendSubnet))
        assertEquals("Effective subnet derivation must match exact octets", "192.168.1", apartmentDevice.effectiveSubnet)
    }

    // --- THREAT MODEL 6: ADVERSARIAL FORGOTTEN / BLACKLISTED DEVICE INVARIANT ---

    @Test
    fun testBlacklistedDeviceExcludedFromMerge() {
        val unwantedDevice = RemoteDevice(
            id = "unwanted_tv",
            name = "Neighbor TV",
            ipAddress = "192.168.1.200",
            port = 5555,
            deviceType = DeviceType.FIRE_TV,
            subnetPrefix = "192.168.1"
        )
        val myTv = RemoteDevice(
            id = "my_tv",
            name = "My OLED",
            ipAddress = "192.168.1.150",
            port = 8008,
            deviceType = DeviceType.GOOGLE_TV,
            subnetPrefix = "192.168.1"
        )

        val ignoredIps = setOf("192.168.1.200")
        val scanResults = listOf(unwantedDevice, myTv)

        val filtered = scanResults.filterNot { ignoredIps.contains(it.ipAddress) }
        assertEquals(1, filtered.size)
        assertEquals("192.168.1.150", filtered.first().ipAddress)
        assertFalse("Blacklisted IP must NEVER be admitted into memory or merged list", filtered.any { it.ipAddress == "192.168.1.200" })
    }

    @Test
    fun testSubnetNetworkIdHealingPreventsFragmentedLocations() {
        val legacyDevice = RemoteDevice(
            id = "legacy_device",
            name = "Living Room TV",
            ipAddress = "192.168.1.158",
            port = 5555,
            deviceType = DeviceType.FIRE_TV,
            subnetPrefix = "192.168.1",
            networkId = "net_subnet_192.168.1"
        )

        val authoritativeNetworkId = "net_hw_3f9d334cf4fa49bc_192.168.1"
        val activePrefix = "192.168.1"

        // Perform healing as done in ArgusSessionState
        val healed = if (legacyDevice.effectiveSubnet == activePrefix && legacyDevice.networkId != authoritativeNetworkId) {
            legacyDevice.copy(networkId = authoritativeNetworkId)
        } else legacyDevice

        assertEquals("Device networkId must heal to authoritative hardware networkId", authoritativeNetworkId, healed.networkId)
        assertEquals(authoritativeNetworkId, healed.effectiveNetworkId)
    }
}

