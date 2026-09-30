# 📱 Sovereign Remote — Zero-Cloud Smart TV & Media Controller

> **Platform:** 100% Native Android (Kotlin + Jetpack Compose)  
> **Privacy Invariant:** Zero Cloud, Zero Tracking, Zero Ads. All commands execute locally on your LAN.  
> **Latency:** $<5\text{ ms}$ direct microsecond response over local Wi-Fi.

---

## 🌟 Overview

**Sovereign Remote** is a sleek, lightweight, tactical native Android remote controller built for smart TVs and network media players (Roku, smart displays, streaming boxes). 

Unlike commercial remote apps from the Google Play Store that demand account logins, track your viewing history, and bombard you with advertisements, Sovereign Remote communicates **directly with your TV over your local home Wi-Fi network**.

---

## 🚀 Key Features

* **⚡ Ultra-Low Latency:** Sends local HTTP/REST commands directly to your TV in microseconds without touching the public internet.
* **🔍 Auto-Discovery (SSDP):** Automatically scans your Wi-Fi subnet using UDP multicast (`239.255.255.250:1900`) and connects to your TV on launch.
* **🎯 Manual IP Override:** Supports direct IP entry if your Wi-Fi router blocks multicast/broadcast packets between client devices.
* **🎛️ Full Tactical D-Pad & Controls:**
  * Power On/Off
  * Directional D-Pad (Up, Down, Left, Right, Select/OK)
  * Volume Up, Volume Down, Instant Mute
  * Back, Home, and Asterisk (*) Options
  * Play, Pause, Rewind, Fast-Forward
* **📳 Haptic Feedback:** High-frequency tactile vibration on every button press for physical remote tactile feel.
* **🖤 Pure Dark Tactical Theme:** AMOLED-optimized deep black (`#090D16`) with electric cyan (`#00E5FF`) and tactical crimson (`#FF1744`) accents.

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
4. Run:
   ```bash
   make devices
   ```
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

```
app/src/main/java/com/rmediatech/sovereignremote/
├── MainActivity.kt                # Single-activity Compose entry point
├── model/
│   └── RemoteDevice.kt            # Device and RemoteKey models
├── discovery/
│   └── SsdpScanner.kt             # UDP SSDP multicast discovery engine
├── protocols/
│   └── RokuClient.kt              # Local ECP HTTP command dispatcher
└── ui/
    ├── theme/                     # Dark tactical typography and colors
    └── components/
        └── RemoteScreen.kt        # Declarative D-Pad, Volume, and Device Selector
```
