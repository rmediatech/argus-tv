package com.rmediatech.sovereignremote.security

/**
 * Sovereign Input & Payload Sanitizer:
 * Enforces strict mathematical invariants and OJP security boundaries.
 * Defends against:
 * 1. CSV / Formula injection (=, +, -, @, \t, \r)
 * 2. ANSI CSI / OSC terminal and log poison escape sequences
 * 3. Malformed IP addresses and port allocation bombs
 */
object SovereignSanitizer {

    private val ANSI_ESCAPE_REGEX = Regex("""\u001B\[[0-?]*[ -/]*[@-~]|\u001B\][^\u0007\u001B]*(\u0007|\u001B\\)""")
    private val CONTROL_CHAR_REGEX = Regex("""[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]""")

    /**
     * Sanitizes external device names, removing ANSI escapes and neutralizing formula prefixes.
     */
    fun sanitizeDeviceName(input: String?): String {
        if (input.isNullOrBlank()) return "Unknown TV"
        var clean = ANSI_ESCAPE_REGEX.replace(input, "")
        clean = CONTROL_CHAR_REGEX.replace(clean, "").trim()

        if (clean.isBlank()) return "Unknown TV"

        // Neutralize formula injection if exported to CSV / spreadsheet reports
        val firstChar = clean[0]
        if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@' || firstChar == '\t' || firstChar == '\r') {
            clean = "'$clean"
        }

        return clean.take(64) // Bound length to prevent allocation bombs
    }

    /**
     * Sanitizes external hardware model strings.
     */
    fun sanitizeModelName(input: String?): String? {
        if (input.isNullOrBlank()) return null
        var clean = ANSI_ESCAPE_REGEX.replace(input, "")
        clean = CONTROL_CHAR_REGEX.replace(clean, "").trim()
        if (clean.isBlank()) return null

        val firstChar = clean[0]
        if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@') {
            clean = "'$clean"
        }

        return clean.take(64)
    }

    /**
     * Strictly validates whether a string is a well-formed IPv4 address (0.0.0.0 to 255.255.255.255).
     */
    fun isValidIpv4(ip: String): Boolean {
        val parts = ip.trim().split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            if (part.isEmpty() || (part.length > 1 && part.startsWith('0'))) return false
            val num = part.toIntOrNull() ?: return false
            num in 0..255
        }
    }

    /**
     * Validates whether a port number is in the standard TCP user/registered range (1..65535).
     */
    fun isValidPort(port: Int): Boolean {
        return port in 1..65535
    }
}
