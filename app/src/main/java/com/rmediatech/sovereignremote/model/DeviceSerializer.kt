package com.rmediatech.sovereignremote.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * One-Job-Principle (OJP): Handles device JSON persistence, sanitization, and deduplication.
 * Guarantees resilience against corrupted formats, missing fields, and formula injection.
 */
object DeviceSerializer {

    private val ANSI_REGEX = Regex("""\u001B\[[;?0-9]*[a-zA-Z]""")

    /**
     * Sanitizes external strings by stripping raw ANSI escape codes and spreadsheet formula injection prefixes.
     */
    fun sanitize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var cleaned = input.replace(ANSI_REGEX, "").trim()
        // Neutralize spreadsheet formula injection payloads (=, +, -, @, \t, \r)
        if (cleaned.startsWith("=") || cleaned.startsWith("+") || cleaned.startsWith("-") || cleaned.startsWith("@")) {
            cleaned = "'$cleaned"
        }
        return cleaned
    }

    /**
     * Serializes a list of RemoteDevices to a clean JSON string.
     */
    fun serialize(devices: List<RemoteDevice>): String {
        val arr = JSONArray()
        for (d in devices) {
            val obj = JSONObject()
            obj.put("id", sanitize(d.id))
            obj.put("name", sanitize(d.name))
            obj.put("ipAddress", d.ipAddress.trim())
            obj.put("port", d.port)
            obj.put("deviceType", d.deviceType.name)
            obj.put("modelName", sanitize(d.modelName))
            obj.put("isEthernet", d.isEthernet)
            obj.put("macAddress", d.macAddress?.trim() ?: "")
            obj.put("subnetPrefix", sanitize(d.subnetPrefix ?: d.effectiveSubnet))
            obj.put("networkId", sanitize(d.networkId ?: ""))
            arr.put(obj)
        }
        return arr.toString()
    }

    /**
     * Deserializes a JSON string into a list of RemoteDevices.
     * Tolerant against missing keys, malformed JSON, and unknown device types.
     */
    fun deserialize(jsonStr: String?): List<RemoteDevice> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<RemoteDevice>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").ifBlank { "dev_$i" }
                val name = obj.optString("name", "Unknown TV")
                val ip = obj.optString("ipAddress", "")
                if (ip.isBlank()) continue

                val port = obj.optInt("port", 8008)
                val typeStr = obj.optString("deviceType", "GENERIC")
                val deviceType = try {
                    DeviceType.valueOf(typeStr)
                } catch (_: Exception) {
                    DeviceType.GENERIC
                }

                val modelName = obj.optString("modelName").takeIf { it.isNotBlank() }
                val isEthernet = obj.optBoolean("isEthernet", false)
                val mac = obj.optString("macAddress").takeIf { it.isNotBlank() }
                val subnetPrefix = obj.optString("subnetPrefix").takeIf { it.isNotBlank() }
                val networkId = obj.optString("networkId").takeIf { it.isNotBlank() }

                list.add(
                    RemoteDevice(
                        id = id,
                        name = name,
                        ipAddress = ip,
                        port = port,
                        deviceType = deviceType,
                        modelName = modelName,
                        isEthernet = isEthernet,
                        macAddress = mac,
                        subnetPrefix = subnetPrefix,
                        networkId = networkId
                    )
                )
            }
        } catch (_: Exception) {
            // Return whatever was successfully parsed or empty
        }
        return list
    }

    /**
     * Merges freshly discovered devices into the existing cached inventory.
     * Preserves existing hardware MAC addresses if the fresh probe lacks them.
     */
    fun merge(existing: List<RemoteDevice>, fresh: List<RemoteDevice>): List<RemoteDevice> {
        val map = existing.associateBy { it.ipAddress }.toMutableMap()
        for (f in fresh) {
            val old = map[f.ipAddress]
            if (old != null) {
                map[f.ipAddress] = f.copy(
                    macAddress = f.macAddress ?: old.macAddress,
                    modelName = f.modelName ?: old.modelName,
                    name = if (f.name.startsWith("Google TV") && !old.name.startsWith("Google TV")) old.name else f.name,
                    subnetPrefix = f.subnetPrefix ?: old.subnetPrefix,
                    networkId = f.networkId ?: old.networkId
                )
            } else {
                map[f.ipAddress] = f
            }
        }
        return map.values.toList().sortedBy { it.name }
    }
}
