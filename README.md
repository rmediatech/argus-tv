# 📱 Argus — Zero-Cloud Smart TV & Media Controller

<div align="center">

[![Download Signed APK](https://img.shields.io/badge/Download-Signed%20APK%20(v1.0.0)-10B981?style=for-the-badge&logo=android&logoColor=black)](https://github.com/rmediatech/argus-tv/releases/download/v1.0.0/argus-v1.0.0.apk)
[![RMediaTech Official](https://img.shields.io/badge/RMediaTech-Official%20Fleet-FFB300?style=for-the-badge&logo=shield&logoColor=black)](https://rmediatech.com)
[![Zero Ads](https://img.shields.io/badge/Zero--Ads-100%25%20Private-00F5FF?style=for-the-badge)](https://rmediatech.com)

</div>

> **Platform:** 100% Native Android (Kotlin + Jetpack Compose)  
> **Privacy Invariant:** Zero Cloud, Zero Tracking, Zero Ads. All commands execute locally on your LAN.  
> **Latency:** $<5\text{ ms}$ direct microsecond response over local Wi-Fi.  
> **Developer:** Crafted by RMediaTech ([rmediatech.com](https://rmediatech.com))  

---

## 🌟 Overview

**Argus** (named after *Argus Panoptes*, the all-seeing guardian of classical Greek lore) is a sleek, lightweight, tactical native Android remote controller built for smart TVs and network media players (Google TV, Android TV, Fire TV, Roku, Samsung Tizen, LG webOS, Sony BRAVIA, Vizio SmartCast, and Wake-on-LAN hosts). 

Unlike commercial remote apps from the Google Play Store that demand account logins, track your viewing history, and bombard you with advertisements, Argus communicates **directly with your TV over your local home Wi-Fi network**.

---

## 🚀 Key Features

* **⚡ Ultra-Low Latency:** Sends local commands directly to your TV in microseconds without touching the public internet.
* **🔍 Auto-Discovery (SSDP & ARP):** Automatically scans your Wi-Fi subnet using UDP multicast (`239.255.255.250:1900`) and TCP protocol probing.
* **🎯 Manual IP Override:** Supports direct IP entry if your Wi-Fi router blocks multicast/broadcast packets between client devices.
* **🎛️ Full Tactical D-Pad & Controls:**
  * Power On/Off (with Wake-on-LAN fallback)
  * Directional D-Pad (Up, Down, Left, Right, Select/OK)
  * Volume Up, Volume Down, Instant Mute
  * Back, Home, and Options / Quick Settings keys
  * Play, Pause, Rewind, Fast-Forward
* **📳 Haptic Feedback:** High-frequency tactile vibration on every button press for physical remote tactile feel.
* **🖤 Pure Dark Tactical Theme:** AMOLED-optimized deep black with electric cyan and tactical accents.
* **🗑️ Sovereign Device Governance:** Red trashcan to permanently forget unwanted network devices, plus an eye toggle to hide/unhide TVs.

---

## 📺 First-Time Smart TV Pairing Guide (By Brand)

To protect your privacy and TV security, modern smart TV operating systems require a **one-time physical authorization** when a new remote app connects over Wi-Fi. Once allowed, Argus reconnects automatically with zero prompts:

| TV Platform / Brand | Port | First-Time TV Screen Prompt | Action Required |
| :--- | :--- | :--- | :--- |
| **Amazon Fire TV** | `5555` | *"Allow USB debugging? RSA fingerprint: ... [✓] Always allow"* | Check **"Always allow"** and click **OK** with your TV remote. |
| **Google TV / Android TV** | `5555` / `8008` | *"Allow USB debugging? [✓] Always allow"* | Check **"Always allow"** and click **OK**. (Port 8008 DIAL fallback). |
| **Roku TV & Streaming Sticks** | `8060` | *None (Instant Connect)* | Zero prompts needed. Connects instantly over local ECP. |
| **Samsung Smart TV (Tizen)** | `8001` / `8002` | *"Allow ArgusRemote to control this TV? [Allow / Deny]"* | Click **Allow** using your TV's physical buttons or remote. |
| **LG webOS TV** | `3000` / `3001` | *"com.rmediatech.sovereignremote requests permission [Accept]"* | Click **Accept** on the TV screen. |
| **Sony BRAVIA** | `80` / `20060` | Direct IRCC SOAP API | Pre-shared key or instant IRCC command execution. |
| **Vizio SmartCast** | `7345` / `9000` | HTTPS Direct API | Connects over local SSL socket. |

---

## 🛠️ Build & Installation (No Android Studio Required!)

This project is built using the standard command line and Gradle wrapper:

### 1. Prerequisites
* **JDK 17 or JDK 21 LTS**
* **Android SDK** (Build-Tools `34.0.0` and Platform `android-34`)
* **ADB** (`android-tools-adb`)

### 2. Fast Commands (Makefile)

```bash
# Compile the debug APK
make build

# Install APK to connected phone (via USB or Wi-Fi)
make install

# Launch the app on your phone
make run

# Stream real-time app logs
make logs
```

---

## 📲 Connecting Your Phone via ADB

### Method A: Via USB Cable (Simplest)
1. On your Android phone, go to **Settings $\to$ About Phone** and tap **Build Number** 7 times to enable **Developer Options**.
2. Go to **Settings $\to$ System $\to$ Developer Options** and enable **USB Debugging**.
3. Plug your phone into your computer with a USB cable.
4. Run `make devices`.
5. Tap **"Allow USB Debugging"** on your phone's screen.
6. Run `make run` — the app builds, installs, and launches automatically!

### Method B: Wireless Debugging (Android 11+)
1. Ensure your phone and computer are on the same Wi-Fi network.
2. In **Developer Options**, enable **Wireless Debugging**.
3. Tap **"Pair device with pairing code"** to view the IP, Port, and 6-digit code.
4. Run:
   ```bash
   adb pair <phone_ip>:<port> <pairing_code>
   adb connect <phone_ip>:<connect_port>
   ```
5. Run `make run`.

---

## 🏛️ Architecture

Argus adheres strictly to the Sovereign Engineering Standard: single-responsibility modularity, zero monolithic sprawl, and zero public cloud dependencies.

```
app/src/main/java/com/rmediatech/sovereignremote/
├── MainActivity.kt                # Thin lifecycle shell (<= 80 lines)
├── model/
│   └── RemoteDevice.kt            # Immutable device models, RemoteKey & liveness enums
├── net/
│   ├── NetworkEnvironmentManager.kt # Dynamic CIDR, active subnet resolution & hardware ID
│   └── WakeOnLan.kt              # Kernel ARP cache parser & magic packet broadcaster
├── discovery/
│   ├── UniversalTvScanner.kt      # Parallel SSDP multicast, ARP cache & TCP sweep engine
│   └── SmartTvClassifier.kt       # IEEE MAC OUI and deep port heuristic classifier
├── protocols/
│   ├── TvProtocolRegistry.kt      # Agnostic protocol router with auto-promotion
│   ├── FireTvProtocol.kt          # Persistent Toybox stream (<1ms) & ADB fallback
│   ├── GoogleTvProtocol.kt        # Direct ADB keyevent + DIAL app launcher
│   ├── RokuProtocol.kt            # Local ECP HTTP/REST wire dispatcher
│   ├── SamsungProtocol.kt         # Tizen WebSocket JSON frame engine
│   ├── LgWebOsProtocol.kt         # webOS SSAP WebSocket handshake & key dispatcher
│   ├── SonyBraviaProtocol.kt      # SOAP IRCC XML envelope dispatcher
│   └── VizioSmartCastProtocol.kt  # SmartCast HTTPS command dispatcher
├── state/
│   ├── ArgusSessionState.kt       # Multi-network state, device blacklist & liveness tracker
│   └── DeviceLivenessTracker.kt   # Non-blocking background health probe
└── ui/
    ├── theme/                     # Tactical dark AMOLED palette & theme presets
    └── components/
        ├── RemoteScreen.kt        # Primary remote command surface
        ├── DeviceSelectionDialog.kt # Scoped network picker, trashcan & hide drawers
        ├── ControllerLayouts.kt   # Tactical D-Pad, Scandi frosted glass & retro pads
        ├── DiagnosticLogsDialog.kt # Live forensic audit log viewer
        └── AboutArgusDialog.kt    # Tactical credentials & pairing guidance
```
