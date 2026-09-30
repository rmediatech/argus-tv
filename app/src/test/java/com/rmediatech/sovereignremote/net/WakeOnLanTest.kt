package com.rmediatech.sovereignremote.net

import org.junit.Assert.*
import org.junit.Test

class WakeOnLanTest {

    @Test
    fun testValidMacFormatsGenerateIdenticalPayload() {
        val macColon = "88:b8:63:08:df:00"
        val macHyphen = "88-b8-63-08-df-00"
        val macBare = "88b86308df00"
        val macUpper = "88:B8:63:08:DF:00"

        val packetColon = WakeOnLan.buildMagicPacket(macColon)
        val packetHyphen = WakeOnLan.buildMagicPacket(macHyphen)
        val packetBare = WakeOnLan.buildMagicPacket(macBare)
        val packetUpper = WakeOnLan.buildMagicPacket(macUpper)

        assertNotNull(packetColon)
        assertNotNull(packetHyphen)
        assertNotNull(packetBare)
        assertNotNull(packetUpper)

        // All formats must resolve to the identical 102-byte payload
        assertArrayEquals(packetColon, packetHyphen)
        assertArrayEquals(packetColon, packetBare)
        assertArrayEquals(packetColon, packetUpper)
    }

    @Test
    fun testMagicPacketByteStructure() {
        val mac = "88:b8:63:08:df:00"
        val packet = WakeOnLan.buildMagicPacket(mac)

        assertNotNull(packet)
        assertEquals(102, packet!!.size)

        // 1. First 6 bytes must be 0xFF
        for (i in 0 until 6) {
            assertEquals("Header byte $i must be 0xFF", 0xFF.toByte(), packet[i])
        }

        // 2. Target MAC bytes: 0x88, 0xB8, 0x63, 0x08, 0xDF, 0x00
        val expectedMacBytes = byteArrayOf(
            0x88.toByte(), 0xB8.toByte(), 0x63.toByte(),
            0x08.toByte(), 0xDF.toByte(), 0x00.toByte()
        )

        // 3. Exactly 16 repetitions of MAC
        for (rep in 0 until 16) {
            val offset = 6 + rep * 6
            for (byteIndex in 0 until 6) {
                assertEquals(
                    "Repetition $rep byte $byteIndex mismatch",
                    expectedMacBytes[byteIndex],
                    packet[offset + byteIndex]
                )
            }
        }
    }

    @Test
    fun testAdversarialMalformedMacInputs() {
        val adversarialPayloads = listOf(
            "",                                  // Empty string
            "   ",                               // Whitespace
            "88:b8",                             // Truncated (too short)
            "88:b8:63:08:df:00:11",              // Extended (too long)
            "GG:HH:II:JJ:KK:LL",                 // Non-hex characters
            "88:b8:63:08:df:ZZ",                 // Partial non-hex
            "88:b8; rm -rf /",                   // Command injection payload
            "<script>alert(1)</script>",         // XSS payload
            "88:b8:63\u0000:08:df:00",           // Null byte injection
            "88:b8:63:08:df:00\n192.168.1.1",    // CRLF injection
            "AA:".repeat(10000),                 // Allocation bomb
            "-1:-1:-1:-1:-1:-1"                  // Negative numbers
        )

        for (malformed in adversarialPayloads) {
            val result = WakeOnLan.buildMagicPacket(malformed)
            assertNull("Adversarial payload '$malformed' should return null instead of throwing", result)
        }
    }
}
