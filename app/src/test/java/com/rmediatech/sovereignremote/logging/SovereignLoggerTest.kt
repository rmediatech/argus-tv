package com.rmediatech.sovereignremote.logging

import org.junit.Assert.*
import org.junit.Test

class SovereignLoggerTest {

    @Test
    fun testRingBufferAccumulationAndBound() {
        for (i in 1..150) {
            SovereignLogger.i(SovereignLogger.Category.DISCOVERY, "Probe iteration $i")
        }

        val logs = SovereignLogger.getRecentLogs(100)
        assertTrue("Ring buffer must hold entries", logs.isNotEmpty())
        assertTrue("Ring buffer must not exceed 100", logs.size <= 100)
        assertTrue("Latest entry must be present", logs.last().contains("Probe iteration 150"))
    }

    @Test
    fun testAnsiAndNewlineScrubbingInLogs() {
        val poisoned = "Alert\u001b[31;1m RED\u001b[0m\nSecond Line"
        SovereignLogger.w(SovereignLogger.Category.SECURITY, poisoned)

        val lastLog = SovereignLogger.getRecentLogs(1).first()
        assertFalse("ANSI escape sequence must be scrubbed", lastLog.contains("\u001b["))
        assertFalse("Newlines must be collapsed into spaces", lastLog.contains("\n"))
        assertTrue("Category header must be present", lastLog.contains("[SECURITY]"))
    }

    @Test
    fun testCompartmentalizedCategories() {
        SovereignLogger.i(SovereignLogger.Category.NETWORK, "Gateway 192.168.1.1 MAC resolved")
        SovereignLogger.i(SovereignLogger.Category.PROTOCOL, "Keyevent KEYCODE_HOME ACK 12ms")
        SovereignLogger.i(SovereignLogger.Category.LIVENESS, "Ping alive")

        val recent = SovereignLogger.getRecentLogs(10)
        assertTrue(recent.any { it.contains("[NETWORK]") && it.contains("Gateway") })
        assertTrue(recent.any { it.contains("[PROTOCOL]") && it.contains("KEYCODE_HOME") })
        assertTrue(recent.any { it.contains("[LIVENESS]") && it.contains("Ping alive") })
    }
}
