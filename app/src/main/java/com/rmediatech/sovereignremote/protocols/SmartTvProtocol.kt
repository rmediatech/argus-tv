package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey

interface SmartTvProtocol {
    val deviceType: DeviceType
    val defaultPort: Int

    suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit>
    suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit>
    suspend fun probe(ip: String, port: Int = defaultPort): RemoteDevice?
}
