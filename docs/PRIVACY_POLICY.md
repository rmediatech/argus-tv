# 🔒 Argus Privacy Policy

> **Effective Date:** November 3, 2026  
> **Entity:** RMediaTech LLC (Pennsylvania, USA)  
> **Contact:** `privacy@rmediatech.com` | [https://rmediatech.com](https://rmediatech.com)  
> **Application:** Argus — Zero-Cloud Smart TV & Media Controller (`com.rmediatech.sovereignremote`)

---

## 🏛️ 1. Sovereign Privacy Covenant

At **RMediaTech LLC**, our engineering standard is founded on **Radical Sovereignty & Zero-Cloud Autonomy** (`AGY-RULE-SOVEREIGN-FLAGSHIP-01`). 

Unlike commercial remote control applications that collect user data, inject pop-up advertisements, track viewing habits, and demand personal account registrations, **Argus is engineered from the ground up with 100% local, peer-to-peer data custody.**

* **Zero Cloud Dependencies:** Argus does not communicate with any external servers, analytics providers, or advertising networks.
* **Zero Telemetry & Tracking:** We do not embed Firebase Analytics, Google Crashlytics, Mixpanel, AppsFlyer, Facebook SDK, or any third-party tracking libraries.
* **Zero Account Registration:** You do not need to create an account, log in, or provide your email address, phone number, or identity.

---

## 📊 2. What Data Is Collected

### **NONE (Zero Personal Data Collected)**
* **No Personal Information:** Argus never accesses, collects, or transmits names, emails, phone numbers, contacts, accounts, photos, or files.
* **No Usage Analytics:** We do not track which buttons you press, how long you use the app, or what channels or videos you watch.
* **No Location Data:** Argus does not request or access GPS location, coarse cellular location, or background geolocation.
* **No Device Fingerprinting:** We do not harvest IMEI, Android ID, advertising IDs (AAID), or hardware serial numbers.

---

## 📡 3. Local Network Communication & Permissions

Argus communicates **exclusively over your local home Wi-Fi network (LAN)** directly with your smart TVs and media streaming devices. All network traffic remains contained within your home router.

### Explicit Permissions Requested & Engineering Justifications:

| Android Permission | Technical Justification | Cloud Transmission |
| :--- | :--- | :--- |
| `android.permission.INTERNET` | Opens direct TCP, HTTP, WebSocket, and ADB sockets to smart TVs on your local subnet (e.g. `192.168.1.x`). | **ZERO. Never routes to public internet.** |
| `android.permission.ACCESS_NETWORK_STATE` | Detects whether your phone is connected to local Wi-Fi before initiating television discovery. | **ZERO. Local OS query only.** |
| `android.permission.ACCESS_WIFI_STATE` | Queries Wi-Fi subnet boundaries (CIDR) to prevent cross-network device misrouting. | **ZERO. Stored only in local RAM.** |
| `android.permission.CHANGE_WIFI_MULTICAST_STATE` | Listens for UPnP/SSDP multicast announcements (`239.255.255.250:1900`) to discover TVs on your home network. | **ZERO. Local broadcast only.** |
| `android.permission.VIBRATE` | Drives AMOLED tactical haptic feedback when pressing remote navigation keys. | **N/A. Pure hardware actuation.** |
| `android.permission.WAKE_LOCK` | Keeps the phone screen awake optionally while viewing movie playhead timecodes during a Watch Party. | **N/A. Local power state management.** |

---

## 💾 4. Local Storage & Cryptographic Custody

* **Local Preference Storage:** Any discovered television names, IP addresses, and pairing tokens are stored **strictly on your physical device** using Android's encrypted local storage (`EncryptedSharedPreferences` / SQLite).
* **Zero Cloud Sync:** Your saved television list is never backed up to remote cloud databases.
* **Immediate Deletion:** You can remove any saved television or clear all cached network profiles at any time by tapping "Forget Device" or clearing the app storage in Android Settings.

---

## 🛡️ 5. Google Play Data Safety Summary

In compliance with Google Play Store Data Safety policies:

* **Data Collected:** No data collected.
* **Data Shared:** No data shared with third parties.
* **Security Practices:**
  * All communications occur strictly over local home network sockets.
  * Encrypted local device storage for authentication pairing tokens.
  * No remote data retention, no external database, no cloud attack surface.

---

## 📬 6. Contact & Legal Inquiries

If you have questions, feedback, or require technical verification of our network isolation, you may contact our engineering team directly:

* **Company:** RMediaTech LLC
* **Address:** Commonwealth of Pennsylvania, USA
* **Email:** `privacy@rmediatech.com`
* **Website:** [https://rmediatech.com](https://rmediatech.com)
* **Open Source Repository & Verification:** [https://github.com/rmediatech/argus-tv](https://github.com/rmediatech/argus-tv)
