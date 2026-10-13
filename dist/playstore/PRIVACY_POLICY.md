# Privacy Policy for Argus Sovereign Remote

**Last Updated:** October 13, 2026  
**Developer:** Rmediatech LLC ([rmediatech.com](https://rmediatech.com))  
**Contact:** `rick@rmediatech.com`  

---

## 🏛️ Our Radical Privacy Invariant

At **Rmediatech LLC**, we believe your personal space and entertainment habits belong solely to you. Unlike commercial applications that harvest viewing habits, track locations, or sell telemetry data to third parties, **Argus is built on the invariant of zero-cloud sovereignty**.

### 1. Zero Data Collection
Argus **does not collect, harvest, store, transmit, or sell** any personally identifiable information, device identifiers, browsing activity, or media consumption habits. 

### 2. Local LAN Execution Only
All network requests issued by Argus are sent strictly over your local Wi-Fi network directly to your connected TV or media device (via standard local SSDP, ECP, and REST protocols). No network traffic ever routes through external cloud servers, intermediate relays, or remote telemetry endpoints.

### 3. Permissions Used
Argus requests only the minimal set of local network permissions necessary to discover and communicate with your TV:
* `INTERNET`: Required by the Android OS to send local HTTP commands to devices on your Wi-Fi subnet.
* `ACCESS_NETWORK_STATE`: Used to detect whether your device is connected to a local Wi-Fi network.
* `ACCESS_WIFI_STATE`: Used to identify the active local Wi-Fi subnet to restrict device discovery strictly to your current location.
* `CHANGE_WIFI_MULTICAST_STATE`: Required to broadcast local SSDP discovery packets to discover smart TVs automatically.

Argus does **not** request or access:
* Precise or approximate GPS location
* Contacts, accounts, or emails
* Microphones, cameras, or audio recording
* SMS, phone state, or call logs
* External device storage

### 4. Zero Third-Party SDKs or Advertising
Argus contains zero third-party tracking libraries, zero analytics SDKs (such as Google Analytics or Firebase), and zero advertising networks.

### 5. Data Retention & Deletion
Because Argus collects zero user data on remote servers, there is no remote data to retain, delete, or manage. Any device nicknames or IP addresses entered by the user are stored strictly inside your phone's local Android private storage and are permanently purged if you clear app data or uninstall the application.

### 6. Contact Information
If you have any questions or feedback regarding this Privacy Policy or our software, please contact:
**Rmediatech LLC**  
5325 N Camac St  
Philadelphia, PA 19141  
Email: `rick@rmediatech.com`  
Web: `https://rmediatech.com`
