package com.rmediatech.sovereignremote.net

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Sovereign Gateway Hardware Fingerprinter (`SOVEREIGN-IDENTITY-ANCHOR-01`):
 * One Job: Probes and caches immutable router TLS/HTTP/UPnP hardware fingerprints
 * to disambiguate identical Layer-3 subnets (e.g. two locations both using 192.168.1.0/24).
 */
object GatewayHardwareProber {
    private const val TAG = "GatewayProber"
    private val cachedGatewayHwMap = ConcurrentHashMap<String, String>()

    fun getCachedGatewayHardwareId(context: Context?, gatewayIp: String?): String? {
        if (gatewayIp.isNullOrBlank()) return null
        cachedGatewayHwMap[gatewayIp]?.let { return it }
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences("sovereign_network_env", Context.MODE_PRIVATE)
                val stored = prefs.getString("gw_hw_$gatewayIp", null)
                if (!stored.isNullOrBlank()) {
                    cachedGatewayHwMap[gatewayIp] = stored
                    return stored
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun recordGatewayHardwareId(context: Context?, gatewayIp: String?, hardwareId: String) {
        val clean = hardwareId.replace("[^a-zA-Z0-9]".toRegex(), "").lowercase().take(16)
        if (clean.isNotBlank() && !gatewayIp.isNullOrBlank()) {
            cachedGatewayHwMap[gatewayIp] = clean
            context?.let { ctx ->
                try {
                    ctx.getSharedPreferences("sovereign_network_env", Context.MODE_PRIVATE)
                        .edit()
                        .putString("gw_hw_$gatewayIp", clean)
                        .apply()
                } catch (_: Exception) {}
            }
            Log.d(TAG, "Recorded gateway hardware fingerprint for $gatewayIp: $clean")
        }
    }

    fun clearCache() {
        cachedGatewayHwMap.clear()
    }

    fun probeGatewayHardwareId(gatewayIp: String, forceFresh: Boolean = false): String? {
        if (gatewayIp.isBlank()) return null
        if (!forceFresh) {
            cachedGatewayHwMap[gatewayIp]?.let { return it }
        }

        // 1. Fast TLS Cert Fingerprint on Port 443 (150ms timeout)
        try {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAll, SecureRandom())
            (sslContext.socketFactory.createSocket() as SSLSocket).use { s ->
                s.soTimeout = 150
                s.connect(InetSocketAddress(gatewayIp, 443), 150)
                s.startHandshake()
                val cert = s.session.peerCertificates.firstOrNull() as? X509Certificate
                if (cert != null) {
                    val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                    val fp = digest.take(8).joinToString("") { "%02x".format(it) }
                    cachedGatewayHwMap[gatewayIp] = fp
                    return fp
                }
            }
        } catch (_: Throwable) {}

        // 2. Fast HTTP Banner / Realm on Port 80 (150ms timeout)
        try {
            Socket().use { s ->
                s.soTimeout = 150
                s.connect(InetSocketAddress(gatewayIp, 80), 150)
                val out = s.getOutputStream()
                out.write("HEAD / HTTP/1.0\r\nHost: $gatewayIp\r\n\r\n".toByteArray(Charsets.US_ASCII))
                out.flush()
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.US_ASCII))
                val sb = StringBuilder()
                var line: String?
                var linesRead = 0
                while (reader.readLine().also { line = it } != null && linesRead++ < 15) {
                    val l = line?.lowercase() ?: continue
                    if (l.startsWith("server:") || l.startsWith("www-authenticate:") || l.startsWith("location:")) {
                        sb.append(line).append(";")
                    }
                }
                if (sb.isNotEmpty()) {
                    val digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().toByteArray(Charsets.UTF_8))
                    val fp = digest.take(8).joinToString("") { "%02x".format(it) }
                    cachedGatewayHwMap[gatewayIp] = fp
                    return fp
                }
            }
        } catch (_: Throwable) {}

        return null
    }
}
