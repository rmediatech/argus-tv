package com.rmediatech.sovereignremote.protocols

import android.content.Context
import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey

class TvProtocolRegistry(private val context: Context) {
    private val protocols = mutableMapOf<DeviceType, SmartTvProtocol>()

    init {
        register(RokuProtocol())
        register(GoogleTvProtocol())
        register(FireTvProtocol(context))
    }

    fun register(protocol: SmartTvProtocol) {
        protocols[protocol.deviceType] = protocol
    }

    fun get(deviceType: DeviceType): SmartTvProtocol {
        return protocols[deviceType] ?: protocols[DeviceType.GOOGLE_TV] ?: protocols.values.first()
    }

    suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> {
        val protocol = get(device.deviceType)
        return protocol.sendKey(device, key)
    }

    suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> {
        val protocol = get(device.deviceType)
        return protocol.launchApp(device, appName)
    }

    fun getProtocols(): List<SmartTvProtocol> = protocols.values.toList()
}
