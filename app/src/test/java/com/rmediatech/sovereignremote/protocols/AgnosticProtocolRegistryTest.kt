package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite validating the Sovereign Agnostic TV Protocol Registry,
 * dynamic candidate ports, SSDP search targets, and IPv6 formatting.
 */
class AgnosticProtocolRegistryTest {

    private val registry = TvProtocolRegistry()

    @Test
    fun testProtocolRegistryContainsAllRegisteredProtocols() {
        val protocols = registry.getAllProtocols()
        assertTrue("Registry must contain multiple protocols", protocols.size >= 8)

        val deviceTypes = protocols.map { it.deviceType }.toSet()
        assertTrue(deviceTypes.contains(DeviceType.ROKU))
        assertTrue(deviceTypes.contains(DeviceType.GOOGLE_TV))
        assertTrue(deviceTypes.contains(DeviceType.FIRE_TV))
        assertTrue(deviceTypes.contains(DeviceType.SAMSUNG_TIZEN))
        assertTrue(deviceTypes.contains(DeviceType.LG_WEBOS))
        assertTrue(deviceTypes.contains(DeviceType.SONY_BRAVIA))
        assertTrue(deviceTypes.contains(DeviceType.VIZIO_SMARTCAST))
        assertTrue(deviceTypes.contains(DeviceType.GENERIC))
    }

    @Test
    fun testCandidatePortsAggregatedWithoutDuplicates() {
        val ports = registry.getAllCandidatePorts()
        assertTrue("Must include Roku port 8060", ports.contains(8060))
        assertTrue("Must include ADB port 5555", ports.contains(5555))
        assertTrue("Must include DIAL port 8008", ports.contains(8008))
        assertTrue("Must include Samsung port 8001", ports.contains(8001))
        assertTrue("Must include LG webOS port 3000", ports.contains(3000))
        assertTrue("Must include Vizio port 7345", ports.contains(7345))
        assertTrue("Must include Sony port 80", ports.contains(80))
        assertTrue("Candidate ports list must have no duplicates", ports.size == ports.distinct().size)
    }

    @Test
    fun testSsdpSearchTargetsAggregatedWithoutDuplicates() {
        val targets = registry.getAllSsdpSearchTargets()
        assertTrue("Must contain roku:ecp target", targets.contains("roku:ecp"))
        assertTrue("Must contain dial-multiscreen target", targets.any { it.contains("dial-multiscreen") })
        assertTrue("Search targets list must have no duplicates", targets.size == targets.distinct().size)
    }

    @Test
    fun testSsdpHeaderDispatchToRoku() {
        val rokuResponse = "HTTP/1.1 200 OK\r\nST: roku:ecp\r\nUSN: uuid:roku:ecp:12345\r\nLocation: http://192.168.1.104:8060/\r\n"
        val matchedProtocol = registry.matchSsdp(rokuResponse)
        assertNotNull("Roku SSDP response must match RokuProtocol", matchedProtocol)
        assertEquals(DeviceType.ROKU, matchedProtocol?.deviceType)
    }

    @Test
    fun testSsdpHeaderDispatchToDial() {
        val dialResponse = "HTTP/1.1 200 OK\r\nST: urn:dial-multiscreen-org:service:dial:1\r\nLocation: http://192.168.1.150:8008/ssdp/device-desc.xml\r\n"
        val matchedProtocol = registry.matchSsdp(dialResponse)
        assertNotNull("DIAL SSDP response must match GenericDialProtocol", matchedProtocol)
        assertEquals(DeviceType.GENERIC, matchedProtocol?.deviceType)
    }

    @Test
    fun testIpv6FormattingInRemoteDevice() {
        val ipv6Device = RemoteDevice(
            id = "test-ipv6",
            name = "IPv6 Living Room TV",
            ipAddress = "fe80::1ff:fe00:3a60",
            port = 8060,
            deviceType = DeviceType.ROKU
        )

        assertTrue(ipv6Device.isIpv6)
        assertEquals("[fe80::1ff:fe00:3a60]", ipv6Device.formattedHost)
        assertEquals("http://[fe80::1ff:fe00:3a60]:8060", ipv6Device.httpBaseUrl)
        assertEquals("fe80::1ff:fe00", ipv6Device.effectiveSubnet)
    }

    @Test
    fun testIpv4FormattingInRemoteDevice() {
        val ipv4Device = RemoteDevice(
            id = "test-ipv4",
            name = "IPv4 Roku TV",
            ipAddress = "192.168.1.104",
            port = 8060,
            deviceType = DeviceType.ROKU
        )

        assertFalse(ipv4Device.isIpv6)
        assertEquals("192.168.1.104", ipv4Device.formattedHost)
        assertEquals("http://192.168.1.104:8060", ipv4Device.httpBaseUrl)
        assertEquals("192.168.1", ipv4Device.effectiveSubnet)
    }
}
