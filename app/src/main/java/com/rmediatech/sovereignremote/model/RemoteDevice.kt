package com.rmediatech.sovereignremote.model

enum class DeviceType(val displayName: String, val badgeColorHex: Long) {
    GOOGLE_TV("Google TV", 0xFF00E5FF),    // Neon Cyan
    FIRE_TV("Fire TV", 0xFFFF9900),        // Amazon Flame Orange
    ROKU("Roku", 0xFFA855F7),              // Roku Electric Purple
    SAMSUNG_TIZEN("Samsung", 0xFF3B82F6),  // Samsung Blue
    LG_WEBOS("LG webOS", 0xFFEC4899),      // LG Ruby
    GENERIC("Smart TV", 0xFF9CA3AF)        // Slate Gray
}

enum class DeviceLivenessState(val label: String, val colorHex: Long) {
    ONLINE("ONLINE", 0xFF00E676),        // Vibrant Neon Green
    STANDBY("STANDBY", 0xFFFFAB00),      // Sunset Amber / Sleep
    WAKING("WAKING...", 0xFF00E5FF),     // Electric Cyan Pulse
    DISCONNECTED("OFFLINE", 0xFF9CA3AF)  // Slate Muted Gray
}

enum class RemoteKey(val code: String) {
    POWER("Power"),
    HOME("Home"),
    BACK("Back"),
    UP("Up"),
    DOWN("Down"),
    LEFT("Left"),
    RIGHT("Right"),
    SELECT("Select"),
    VOLUME_UP("VolumeUp"),
    VOLUME_DOWN("VolumeDown"),
    MUTE("VolumeMute"),
    PLAY_PAUSE("Play"),
    REWIND("Rev"),
    FAST_FORWARD("Fwd"),
    INFO("Info")
}

data class RemoteDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val deviceType: DeviceType,
    val modelName: String? = null,
    val isEthernet: Boolean = false,
    val macAddress: String? = null,
    val subnetPrefix: String? = null,
    val networkId: String? = null
) {
    val effectiveSubnet: String
        get() = subnetPrefix?.takeIf { it.isNotBlank() } ?: ipAddress.substringBeforeLast(".", "")

    val effectiveNetworkId: String
        get() = networkId?.takeIf { it.isNotBlank() } ?: "net_subnet_$effectiveSubnet"
}

