# 🎬 Sovereign Cinema & Multi-Room Watch Party Guide

> **Ecosystem Component:** Argus (Android) & Argus (Rust CLI)  
> **Architecture Standard:** One-Job-Principle (OJP), Zero-Cloud Autonomy, Local Subnet Custody  
> **Privacy Invariant:** 100% Local Wi-Fi, Zero Accounts, Zero Cloud Telemetry, Zero Ads  

---

## 🏛️ 1. Overview & One-Job-Principle (OJP) Boundaries

Traditional streaming and "watch party" solutions (Teleparty, Scener, proprietary Smart TV apps) route your private viewing sessions, keystrokes, and playback timestamps through commercial cloud servers and third-party analytics trackers.

**Argus Sovereign Cinema** decouples media observation and playback coordination into strictly modular, single-responsibility components:

| Component | Responsibility (One Job) | Wire Protocol |
| :--- | :--- | :--- |
| **`CinemaBeamDialog`** | Captures phone keyboard queries/URLs and toggles multi-room sync targets. | In-memory Jetpack Compose State |
| **`ArgusMediaCoordinator`** | Polls hardware playhead telemetry and broadcasts lockstep Play/Pause signals. | Reactive Kotlin Coroutine Flow |
| **`TvMediaBridge`** | Interrogates active playback state on Fire TV / Google TV without touching protected DRM video surfaces. | Local ADB / REST Wire Protocol |
| **`argus cinema` (Rust)** | High-precision terminal HUD tracking playhead milliseconds and live `.srt` dialogue cues. | Musl Static CLI Daemon |

---

## 📺 2. Step-by-Step Operator Instructions

### Step 1: Target Selection & Initial Pairing
1. Launch **Argus Remote** on your Android device connected to your local Wi-Fi network.
2. Select your primary ("Master") TV from the top target picker (e.g. `Living Room Fire TV` or `Bedroom Google TV`).
3. If connecting for the first time, check **"Always allow"** and click **OK** on the TV screen prompt when prompted.

---

### Step 2: Beaming Movie Searches or Media URLs (No D-Pad Pecking)
Searching for movies or navigating URLs using a standard directional D-pad on an on-screen keyboard is slow and tedious.

1. On the main remote screen, tap the **`🎬 Beam Movie / Watch Party Sync`** pill located just above the media playback buttons.
2. In the **BEAM MOVIE SEARCH OR YOUTUBE URL** input box:
   * **Movie Title**: Enter title (e.g. `Needful Things 1993` or `Dreamcatcher`).
   * **Direct URL**: Paste any YouTube, video, or web link.
3. Tap **"Beam Straight to TV"**.
4. Argus immediately launches the target application (YouTube, Prime Video, or Default Browser) and delivers the query in milliseconds.

---

### Step 3: Multi-TV Watch Party Sync (Universal Lockstep Parity)
If you have multiple Smart TVs or displays in different rooms (e.g. Living Room, Bedroom, Garage):

1. Open the **Cinema Beam Dialog** by tapping the **`🎬`** pill.
2. Scroll to the **MULTI-TV WATCH PARTY SYNC** section.
3. You will see all other discovered TVs on your home network.
4. **Tap any TV to toggle synchronization**:
   * When marked **`SYNCED`**, that device enters lockstep follower mode.
   * A luminous **WATCH PARTY SHIELD** HUD chip appears on the main remote showing screens linked and status.
5. **Universal Key & D-Pad Parity**:
   * **Directional D-Pad**: Pressing `UP`, `DOWN`, `LEFT`, `RIGHT`, `SELECT`, `BACK`, or `HOME` broadcasts to all synced TVs in unison. Surf live channel guides (Pluto TV) or browse menus across displays simultaneously.
   * **Media Transport**: `PLAY_PAUSE`, `PLAY`, `PAUSE`, `REWIND`, `FAST_FORWARD` operate in strict parallel lockstep.
   * **Acoustic Volume**: `VOLUME_UP`, `VOLUME_DOWN`, and `MUTE` adjust all displays in lockstep.
6. **🛑 One-Touch Unison Teardown**:
   * Tap the red **STOP PARTY** pill on the remote HUD.
   * Argus immediately dispatches concurrent `PAUSE` and `HOME` commands to every linked TV in parallel, releases sleep guards, and seamlessly reverts control to single-device unicast on your selected TV.

---

### Step 4: Live Hardware Playhead Observation
Unlike cloud scrapers that guess video progress, Argus directly inspects non-DRM OS media session clocks:

* **Kernel-Anchored Milliseconds**: Calculated by combining base media session timestamps with kernel wake uptime, preventing timecode drift during network jitter.
* **Playback States**:
  * `PLAYING` (Active forward tick at current playback rate, e.g. 1.0x).
  * `PAUSED` (Static playhead lock).
  * `BUFFERING` (Awaiting packet buffer).
  * `IDLE` (No active video surface).

---

### Step 5: Synchronizing with the Rust Terminal Bridge (`argus cinema`)
For lone developers, power users, or AI pair-programmers working at the workstation:

```bash
# 1. Query live playhead from your workstation or terminal
argus cinema --host 192.168.1.158:5555

# 2. Synchronize real-time movie dialogue cues against a local SRT file
argus cinema --host 192.168.1.158:5555 --srt /path/to/movie.srt
```

Output:
```text
🎬 [com.amazon.firetv.youtube] Needful Things (1993) | PLAYING | Playhead: 01:47:26 (6446033 ms) | Screen Awake: true
   [01:47:29] No more killing.
   [01:47:32] Not in Castle Rock! Not me, not you, not anybody!
   [01:47:36] This shit stops now!
```

---

## 📊 3. Real-World Output & What to Expect (Generic & Sanitized)

The Argus Cinema Engine renders consistent, clean telemetry across any smart TV model or brand. Below is what operators see on their device screen during typical real-world use cases:

### Scenario A: Idle / App Launcher (No Video Playing)
When your TV is sitting on the home screen or app launcher:

```text
┌────────────────────────────────────────────────────────┐
│ SOVEREIGN CINEMA & WATCH PARTY          Quick Steps ℹ️ │
├────────────────────────────────────────────────────────┤
│ Living Room TV • IDLE                                  │
│ Ready to beam or track playback                        │
├────────────────────────────────────────────────────────┤
│ BEAM MOVIE SEARCH OR YOUTUBE URL                       │
│ [ e.g. Stephen King Needful Things or URL            ] │
│ [              BEAM STRAIGHT TO TV                   ] │
└────────────────────────────────────────────────────────┘
```

---

### Scenario B: Active Movie Stream (Hardware Clocks Engaged)
Once a stream starts playing on YouTube, Netflix, Prime Video, or a local media player:

```text
┌────────────────────────────────────────────────────────┐
│ SOVEREIGN CINEMA & WATCH PARTY          Quick Steps ℹ️ │
├────────────────────────────────────────────────────────┤
│ YouTube • PLAYING                                      │
│ Stephen King's Needful Things (1993) [Full Movie]      │
│ Hardware Playhead: 01:23:36 (5016278 ms)               │
├────────────────────────────────────────────────────────┤
│ BEAM MOVIE SEARCH OR YOUTUBE URL                       │
│ [ Stephen King Needful Things                        ] │
│ [              BEAM STRAIGHT TO TV                   ] │
└────────────────────────────────────────────────────────┘
```
* **Hardware Playhead**: Automatically increments in 1-second ticks locally without querying the network on every second, preserving phone battery and Wi-Fi bandwidth.

---

### Scenario C: Multi-Room Watch Party Active (Multi-TV Lockstep)
When you have synced additional displays in your home:

```text
┌────────────────────────────────────────────────────────┐
│ MULTI-TV WATCH PARTY SYNC (PLAY/PAUSE LOCKSTEP)        │
├────────────────────────────────────────────────────────┤
│ Bedroom TV                                             │
│ 192.168.1.102 • PLAYING (01:23:36)            [SYNCED] │
├────────────────────────────────────────────────────────┤
│ Kitchen Smart Display                                  │
│ 192.168.1.103 • PLAYING (01:23:35)            [SYNCED] │
├────────────────────────────────────────────────────────┤
│ Basement Projector                                     │
│ 192.168.1.104                             [TAP TO SYNC]│
└────────────────────────────────────────────────────────┘
```
* **Lockstep Latency**: Play/pause signals are dispatched concurrently across local TCP sockets with sub-5ms delta, ensuring audio in adjacent rooms does not produce noticeable echo.

---

## 🛡️ 4. Security & Resilience Invariants

1. **Zero DRM Surface Interference**: Never touches, scrapes, or captures encrypted video memory surfaces (HDCP/Widevine protected buffers). All telemetry is derived strictly from public OS session state descriptors.
2. **Formula Injection Neutralization**: All title strings and metadata queries are sanitized with single-quote escaping (`'`) before display or export to prevent CSV/spreadsheet formula execution.
3. **Sleeping HDMI Self-Healing**: If a TV screen goes to sleep while media is actively marked `PLAYING`, the engine injects a non-invasive `KEYEVENT_WAKEUP` (code `224`) to restore display output without restarting the video stream.
4. **Blackhole Probe Resilience**: Multi-room sync probes enforce a strict 50ms socket ceiling, ensuring offline or unplugged TVs never block or lag remote responsiveness.
