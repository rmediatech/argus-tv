package com.rmediatech.sovereignremote.protocols

import com.rmediatech.sovereignremote.model.DeviceType
import com.rmediatech.sovereignremote.model.RemoteDevice
import com.rmediatech.sovereignremote.model.RemoteKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class RokuProtocol : SmartTvProtocol {
    override val protocolId: String = "roku"
    override val deviceType: DeviceType = DeviceType.ROKU
    override val defaultPort: Int = 8060

    override val signature = ProtocolSignature(
        protocolId = protocolId,
        deviceType = deviceType,
        ssdpSearchTargets = listOf("roku:ecp"),
        ssdpHeaderMatchers = listOf("roku:ecp", ":8060"),
        candidatePorts = listOf(8060)
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .writeTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .build()

    override suspend fun sendKey(device: RemoteDevice, key: RemoteKey): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${device.httpBaseUrl}/keypress/${key.code}"
            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody())
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Roku HTTP Error: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun launchApp(device: RemoteDevice, appName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "${device.httpBaseUrl}/launch/$appName"
            val request = Request.Builder().url(url).post("".toRequestBody()).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit) else Result.failure(IOException("Launch failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun probe(host: String, port: Int): RemoteDevice? = withContext(Dispatchers.IO) {
        try {
            val formattedHost = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
            val url = "http://$formattedHost:$port/query/device-info"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val xml = response.body?.string().orEmpty()
                    val nameMatch = Regex("<friendly-device-name>(.*?)</friendly-device-name>").find(xml)
                    val modelMatch = Regex("<model-name>(.*?)</model-name>").find(xml)
                    val wifiMacMatch = Regex("<wifi-mac>(.*?)</wifi-mac>").find(xml)
                    val ethMacMatch = Regex("<ethernet-mac>(.*?)</ethernet-mac>").find(xml)

                    val isEthernet = ethMacMatch != null && ethMacMatch.groupValues[1].isNotBlank()
                    val mac = ethMacMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                        ?: wifiMacMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                        ?: com.rmediatech.sovereignremote.net.WakeOnLan.getMacFromArp(host)

                    val name = nameMatch?.groupValues?.get(1)?.ifBlank { "Roku Device" } ?: "Roku Device"
                    val model = modelMatch?.groupValues?.get(1)

                    RemoteDevice(
                        id = "roku_$host",
                        name = name,
                        ipAddress = host,
                        port = port,
                        deviceType = DeviceType.ROKU,
                        modelName = model?.let { "Roku $it" } ?: "Roku",
                        isEthernet = isEthernet,
                        macAddress = mac?.lowercase()
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun probeSsdp(host: String, port: Int, ssdpPayload: String): RemoteDevice? {
        // Roku devices respond to M-SEARCH with location pointing to :8060
        return probe(host, 8060)
    }
}
