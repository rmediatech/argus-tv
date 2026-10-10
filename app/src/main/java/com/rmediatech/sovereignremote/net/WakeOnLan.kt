package com.rmediatech.sovereignremote.net

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {
    private const val TAG = "WakeOnLan"

    /**
     * Constructs a standard 102-byte Wake-on-LAN Magic Packet payload.
     * Consists of 6 bytes of 0xFF followed by 16 repetitions of the target MAC address.
     * Returns null if the MAC address is invalid or malformed.
     */
    fun buildMagicPacket(macAddress: String): ByteArray? {
        val cleanMac = macAddress.replace("[:-]".toRegex(), "").trim()
        if (cleanMac.length != 12) return null
        return try {
            val macBytes = ByteArray(6) { i ->
                cleanMac.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            val packetData = ByteArray(6 + 16 * 6)
            for (i in 0 until 6) {
                packetData[i] = 0xFF.toByte()
            }
            for (i in 0 until 16) {
                System.arraycopy(macBytes, 0, packetData, 6 + i * 6, 6)
            }
            packetData
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Broadcasts Wake-on-LAN (WoL) Magic Packets to wake sleeping Smart TVs.
     * Transmits across 255.255.255.255, directed subnet broadcast, and unicast.
     * Acquires Android WifiManager.MulticastLock when context is provided to prevent Wi-Fi radio packet filtering.
     */
    suspend fun wake(
        macAddress: String,
        ipAddress: String? = null,
        context: Context? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val packetData = buildMagicPacket(macAddress)
        if (packetData == null) {
            Log.e(TAG, "Invalid MAC address for WoL: '$macAddress'")
            return@withContext false
        }

        val wifiManager = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = try {
            wifiManager?.createMulticastLock("ArgusWakeOnLan")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire MulticastLock: ${e.message}")
            null
        }

        try {
            val targets = mutableListOf<InetAddress>()
            // 1. Universal broadcast
            try {
                targets.add(InetAddress.getByName("255.255.255.255"))
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving 255.255.255.255: ${e.message}")
            }

            // 2. Subnet directed broadcast & unicast
            if (!ipAddress.isNullOrBlank()) {
                val parts = ipAddress.split(".")
                if (parts.size == 4) {
                    try {
                        targets.add(InetAddress.getByName("${parts[0]}.${parts[1]}.${parts[2]}.255"))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed resolving subnet broadcast: ${e.message}")
                    }
                    try {
                        targets.add(InetAddress.getByName(ipAddress))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed resolving unicast IP $ipAddress: ${e.message}")
                    }
                }
            }

            DatagramSocket().use { socket ->
                socket.broadcast = true
                // Rapid burst 3 times on ports 9 and 7 across targets
                repeat(3) { burst ->
                    for (port in listOf(9, 7)) {
                        for (target in targets) {
                            try {
                                val packet = DatagramPacket(packetData, packetData.size, target, port)
                                socket.send(packet)
                                Log.d(TAG, "Burst $burst: Sent WoL magic packet to $target:$port for MAC $macAddress")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed sending WoL to $target:$port: ${e.message}")
                            }
                        }
                    }
                    if (burst < 2) {
                        delay(20)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed transmitting WoL magic packets: ${e.message}", e)
            false
        } finally {
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not release MulticastLock: ${e.message}")
            }
        }
    }

    /**
     * Attempts to resolve the MAC address for a given IP address from the kernel ARP table.
     */
    fun getMacFromArp(ipAddress: String): String? {
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                var line = reader.readLine() // Skip header
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line?.split(Regex("\\s+")) ?: continue
                    if (tokens.size >= 4 && tokens[0] == ipAddress) {
                        val mac = tokens[3].lowercase()
                        if (mac != "00:00:00:00:00:00" && mac.length == 17) {
                            return mac
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read /proc/net/arp: ${e.message}")
        }
        return null
    }

    /**
     * Reads all IP to MAC mappings from the local ARP cache.
     */
    fun getAllArpMappings(): Map<String, String> {
        val mappings = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                var line = reader.readLine() // Skip header
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line?.split(Regex("\\s+")) ?: continue
                    if (tokens.size >= 4) {
                        val ip = tokens[0]
                        val mac = tokens[3].lowercase()
                        if (mac != "00:00:00:00:00:00" && mac.length == 17) {
                            mappings[ip] = mac
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read /proc/net/arp: ${e.message}")
        }
        return mappings
    }
}
