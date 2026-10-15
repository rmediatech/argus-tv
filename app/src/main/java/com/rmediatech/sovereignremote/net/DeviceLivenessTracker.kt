package com.rmediatech.sovereignremote.net

import com.rmediatech.sovereignremote.model.DeviceLivenessState
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object DeviceLivenessTracker {

    suspend fun probeLiveness(device: RemoteDevice, timeoutMs: Int = 200): DeviceLivenessState = withContext(Dispatchers.IO) {
        val portsToTry = mutableListOf(device.port)
        when (device.deviceType) {
            DeviceType.GOOGLE_TV -> {
                if (!portsToTry.contains(8008)) portsToTry.add(8008)
                if (!portsToTry.contains(5555)) portsToTry.add(5555)
            }
            DeviceType.FIRE_TV -> {
                if (!portsToTry.contains(5555)) portsToTry.add(5555)
            }
            DeviceType.ROKU -> {
                if (!portsToTry.contains(8060)) portsToTry.add(8060)
            }
            DeviceType.SAMSUNG_TIZEN -> {
                if (!portsToTry.contains(8001)) portsToTry.add(8001)
                if (!portsToTry.contains(8002)) portsToTry.add(8002)
            }
            DeviceType.LG_WEBOS -> {
                if (!portsToTry.contains(3000)) portsToTry.add(3000)
                if (!portsToTry.contains(3001)) portsToTry.add(3001)
            }
            DeviceType.SONY_BRAVIA -> {
                if (!portsToTry.contains(80)) portsToTry.add(80)
                if (!portsToTry.contains(8008)) portsToTry.add(8008)
                if (!portsToTry.contains(20060)) portsToTry.add(20060)
            }
            DeviceType.VIZIO_SMARTCAST -> {
                if (!portsToTry.contains(7345)) portsToTry.add(7345)
                if (!portsToTry.contains(9000)) portsToTry.add(9000)
            }
            else -> {
                if (!portsToTry.contains(8008)) portsToTry.add(8008)
                if (!portsToTry.contains(80)) portsToTry.add(80)
            }
        }

        for (p in portsToTry) {
            if (isPortOpen(device.ipAddress, p, timeoutMs)) {
                return@withContext DeviceLivenessState.ONLINE
            }
        }
        DeviceLivenessState.STANDBY
    }

    fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
