# 📱 Argus — Google Play Store Turnkey Submission Kit

> **Target Release:** `v1.3.5` (`versionCode = 10`)  
> **Package Identifier:** `com.rmediatech.sovereignremote`  
> **Developer Entity:** `RMediaTech LLC`  
> **Prepared For:** Wednesday Console Launch & Registration

---

## 📋 1. Store Listing Metadata & ASO Optimization

### A. App Name / Title (Max 30 Characters)
```text
Argus: Private Smart TV Remote
```

### B. Short Description (Max 80 Characters)
```text
Zero-ads, zero-cloud private Smart TV remote & multi-room watch party controller.
```
*(Length: 80 characters — exact maximum density)*

### C. Full Description (Max 4,000 Characters)
```text
Tired of TV remote apps that track your viewing habits, demand email logins, and blast you with intrusive video ads every time you press volume up?

Welcome to Argus — the zero-cloud, zero-tracking, ad-free smart television remote and media coordinator engineered for pure speed, total privacy, and multi-room entertainment.

Argus communicates directly with your televisions over your local home Wi-Fi network. No intermediate cloud servers, no account creation, and zero surveillance telemetry. What you watch on your TV stays on your TV.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🌟 KEY CAPABILITIES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📱 UNIVERSAL HARDWARE COMPATIBILITY
Control your entire home theater ecosystem from one sleek AMOLED interface:
• Google TV & Android TV (Hisense, TCL, Sony, Philips, Chromecast with Google TV)
• Amazon Fire TV (Fire TV Stick 4K Max, Cube, Omni Series)
• Roku TV & Streaming Players (TCL Roku, Hisense Roku, Express, Ultra)
• Samsung Tizen Smart TVs
• LG webOS Smart TVs
• Sony BRAVIA (Direct IP & IRCC protocols)
• Vizio SmartCast TVs
• Wake-on-LAN (WoL) network power activation

🎉 MULTI-ROOM WATCH PARTY & LOCKSTEP SYNC
Host movies and watch parties across disparate screens in different rooms simultaneously:
• Synchronized Playback: Start, pause, rewind, and fast-forward all synced displays in lockstep.
• Slipstream Drift Auto-Healing: Automatically detects acoustic echo or network drift between rooms and silently aligns timecodes back to sub-50ms synchronization without manual fiddling.
• Unified Volume Rocker: Adjust audio levels across every active room display simultaneously from your phone's volume rocker.

🎬 CINEMA BEAMING (MULTI-SOURCE STREAMING)
Deep-link content directly to one screen or your entire watch party fleet:
• YouTube: Search titles or paste links; automatically bypasses multi-account profile friction.
• Prime Video: Instant title search and direct launch.
• Pluto TV: Streamlined channel and on-demand show beaming.
• Direct Streams: Broadcast custom MP4, HLS, or web video streams.

⚡ ZERO-LAG MICROSECOND RESPONSE
• Direct local socket communication delivers under 5ms command execution.
• Tactical AMOLED D-Pad with customizable high-frequency haptic vibration feedback.
• Automatic background subnet scanning discovers new televisions on your network in seconds.

🔒 RADICAL PRIVACY & ZERO ADVERTISEMENTS
• 100% Ad-Free: No banner ads, no popups, no subscriptions.
• Zero Tracking SDKs: No Google Analytics, no Facebook SDK, no telemetry beacons.
• Local Data Custody: Device pairing tokens and saved networks remain encrypted on your physical device.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🛠️ PERMISSIONS & TRANSPARENCY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Argus requests only the bare minimum permissions necessary for local hardware control:
• Local Network & Wi-Fi: Required to discover and send key commands to televisions on your home router.
• Haptics: Required for tactile feedback upon remote button presses.
• Zero permissions requested for Camera, Microphone, Contacts, Location, or Storage.

Crafted with care by RMediaTech LLC.
Sovereign engineering for independent humans.
```

---

## 🛡️ 2. Google Play Data Safety Form Questionnaire Guide

When filling out the Google Play Console **Data Safety** section:

| Question / Section | Exact Answer | Details / Explanation |
| :--- | :--- | :--- |
| **Does your app collect or share any of the required user data types?** | **NO** | The app collects and transmits zero personal or device data. |
| **Is all of the user data collected by your app encrypted in transit?** | **N/A** (or Yes if asked for local storage) | Data is never transmitted to external cloud servers. |
| **Do you provide a way for users to request that their data be deleted?** | **YES** | Users can delete all saved TV profiles in app settings or clear app storage. |
| **Location Data collected?** | **NO** | Zero location collected. |
| **Personal Info (Name, Email, etc.)?** | **NO** | Zero personal info. |
| **Financial Info?** | **NO** | Zero financial info. |
| **Health and Fitness?** | **NO** | Zero health info. |
| **Messages / Photos / Audio / Files?** | **NO** | Zero media access. |
| **App Activity / Page Views / Clicks?** | **NO** | Zero analytics SDKs. |
| **App Info and Performance (Crash logs)?** | **NO** | Zero crash reporting SDKs embedded. |
| **Device or Other Identifiers?** | **NO** | Zero advertising ID (AAID) or hardware tracking. |

---

## 🎨 3. Store Graphic Asset Registry

Assets located at `assets/play_store/`:

| Asset File | Exact Resolution | Format | Purpose | Status |
| :--- | :--- | :--- | :--- | :--- |
| `icon_512x512.png` | **512 x 512** | 32-bit PNG (RGBA) | High-Resolution App Icon | ✅ Ready (77 KB) |
| `feature_graphic_1024x500.png` | **1024 x 500** | 24-bit PNG (RGB, no alpha) | Feature Graphic Banner | ✅ Ready (91 KB) |
| `argus_emblem.svg` | Vector (Scalable) | SVG | Vector Master | ✅ Ready |

---

## 🏢 4. App Categorization & Content Rating

* **App Category:** Tools / Entertainment
* **Tags / Keywords:** `Smart TV Remote`, `Universal Remote`, `Fire TV`, `Google TV`, `Roku Remote`, `Ad-Free Remote`, `Privacy Remote`
* **Target Age:** 13+ / All Ages
* **Ads Declaration:** Select **"No, my app does not contain ads"**.
* **Target Audience:** All regions / Global release.
* **Support Email:** `support@rmediatech.com`
* **Privacy Policy URL:** `https://github.com/rmediatech/argus-tv/blob/master/docs/PRIVACY_POLICY.md` (or `https://rmediatech.com/argus/privacy`)

---

## 🚀 5. Wednesday Developer Console Submission Checklist

Follow these steps once you access Google Play Console:

1. **Pay Developer Registration Fee ($25 USD)**:
   - Use **Mercury Business Checking Virtual Card** (`Mercury Checking ••7703`).
   - *Tax Invariant (`RMED-CORP-EDU-01`)*: Save the receipt PDF in `accounting/expenses/2026/` as a 100% deductible Schedule C software development operational business expense.
2. **Create New Application**:
   - App Name: `Argus: Private Smart TV Remote`
   - Default Language: `English (United States) - en-US`
   - App or Game: `App`
   - Free or Paid: `Free`
3. **Upload Graphical Assets**:
   - App Icon: Upload `assets/play_store/icon_512x512.png`
   - Feature Graphic: Upload `assets/play_store/feature_graphic_1024x500.png`
   - Screenshots: Capture phone screenshots (or use device emulator shots).
4. **Fill In App Details**:
   - Copy & paste Short Description and Full Description from Section 1 above.
   - Enter Privacy Policy URL.
5. **Complete Data Safety Declaration**:
   - Complete using the exact answers in Section 2 above (Everything is **NO**; zero data collection).
6. **Upload Production Release Bundle**:
   - Go to **Production** -> **Create New Release**.
   - Upload `dist/release-v1.3.5/argus-v1.3.5.aab` (or direct unsigned/signed release build).
   - Release Name: `1.3.5 - Universal Lockstep D-Pad & Cinema Watch Party Engine`
   - Release Notes: Copy from [CHANGELOG.md](file:///home/emhcet/private/projects/desktop/android/argus/CHANGELOG.md).
7. **Submit for Review**:
   - Click **Review release** -> **Start rollout to Production**!
