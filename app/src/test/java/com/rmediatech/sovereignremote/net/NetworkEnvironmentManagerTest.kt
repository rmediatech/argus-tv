package com.rmediatech.sovereignremote.net

import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class NetworkEnvironmentManagerTest {

    @Test
    fun testSubnetCalculationForStandardSlash24() {
        val addr = InetAddress.getByName("192.168.1.150") as java.net.Inet4Address
        val info = NetworkEnvironmentManager.computeSubnetInfo(addr, 24)

        assertNotNull(info)
        assertEquals("192.168.1.150", info!!.localIp)
        assertEquals("192.168.1", info.subnetPrefix)
        assertEquals("192.168.1.0/24", info.cidr)
        assertEquals(24, info.prefixLength)
        assertEquals("192.168.1.255", info.broadcastIp)
    }

    @Test
    fun testSubnetCalculationForComcastXfinityTenDotZero() {
        // Typical Lansdale Comcast Xfinity subnet: 10.0.0.x/24
        val addr = InetAddress.getByName("10.0.0.45") as java.net.Inet4Address
        val info = NetworkEnvironmentManager.computeSubnetInfo(addr, 24)

        assertNotNull(info)
        assertEquals("10.0.0.45", info!!.localIp)
        assertEquals("10.0.0", info.subnetPrefix)
        assertEquals("10.0.0.0/24", info.cidr)
        assertEquals("10.0.0.255", info.broadcastIp)
    }

    @Test
    fun testCandidateIpGenerationBounds() {
        val addr = InetAddress.getByName("192.168.0.88") as java.net.Inet4Address
        val info = NetworkEnvironmentManager.computeSubnetInfo(addr, 24)
        val candidates = NetworkEnvironmentManager.generateCandidateIps(info)

        // Must generate exactly 254 candidate hosts for /24
        assertEquals(254, candidates.size)
        assertEquals("192.168.0.1", candidates.first())
        assertEquals("192.168.0.254", candidates.last())
    }

    @Test
    fun testCandidateIpsFallbackWhenSubnetUnknown() {
        // When network is unknown, bounds candidate IPs to top 3 home subnets (254 * 3 = 762)
        val candidates = NetworkEnvironmentManager.generateCandidateIps(null)
        assertEquals(254 * 3, candidates.size)
        assertTrue(candidates.contains("192.168.1.1"))
        assertTrue(candidates.contains("192.168.0.1"))
        assertTrue(candidates.contains("10.0.0.1"))
    }

    @Test
    fun testSubnetMatchingAndPrefixExtraction() {
        assertEquals("192.168.1", NetworkEnvironmentManager.extractSubnetPrefix("192.168.1.150"))
        assertEquals("10.0.0", NetworkEnvironmentManager.extractSubnetPrefix("10.0.0.12"))
        assertEquals("", NetworkEnvironmentManager.extractSubnetPrefix("invalid-ip"))

        assertTrue(NetworkEnvironmentManager.matchesSubnet("192.168.1.150", "192.168.1"))
        assertFalse(NetworkEnvironmentManager.matchesSubnet("10.0.0.45", "192.168.1"))
        assertTrue(NetworkEnvironmentManager.matchesSubnet("10.0.0.45", "10.0.0"))
    }

    @Test
    fun testRemoteDeviceEffectiveSubnetDerivation() {
        val devHome = RemoteDevice(
            id = "tv_home",
            name = "Living Room TV",
            ipAddress = "192.168.1.151",
            port = 8008,
            deviceType = DeviceType.GOOGLE_TV
        )
        val devFriend = RemoteDevice(
            id = "tv_friend",
            name = "Friend's Roku",
            ipAddress = "10.0.0.50",
            port = 8060,
            deviceType = DeviceType.ROKU,
            subnetPrefix = "10.0.0"
        )

        assertEquals("192.168.1", devHome.effectiveSubnet)
        assertEquals("10.0.0", devFriend.effectiveSubnet)
    }
}
