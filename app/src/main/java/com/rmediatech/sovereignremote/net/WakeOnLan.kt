package com.rmediatech.sovereignremote.net

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
     * Broadcasts Wake-on-LAN (WoL) Magic Packets to wake sleeping Smart TVs.
     * Transmits across 255.255.255.255, directed subnet broadcast, and unicast.
     */
    suspend fun wake(macAddress: String, ipAddress: String? = null): Boolean = withContext(Dispatchers.IO) {
        val cleanMac = macAddress.replace("[:-]".toRegex(), "").trim()
        if (cleanMac.length != 12) {
            Log.e(TAG, "Invalid MAC address for WoL: '$macAddress'")
            return@withContext false
        }

        try {
            val macBytes = ByteArray(6) { i ->
                cleanMac.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }

            // Magic Packet: 6 bytes of 0xFF followed by 16 repetitions of the MAC (102 bytes total)
            val packetData = ByteArray(6 + 16 * 6)
            for (i in 0 until 6) {
                packetData[i] = 0xFF.toByte()
            }
            for (i in 0 until 16) {
                System.arraycopy(macBytes, 0, packetData, 6 + i * 6, 6)
            }

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
                // Burst 3 times on ports 9 and 7 to ensure delivery over Wi-Fi
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
                        delay(60)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed transmitting WoL magic packets: ${e.message}", e)
            false
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
