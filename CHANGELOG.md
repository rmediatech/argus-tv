# Changelog

All notable changes to **Argus (Android)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-11-03

### Added
- **SST Multi-Platform Cinema Source Architecture (`CinemaSource.kt`)**: Single Source of Truth (SST) streaming source model supporting **YouTube**, **Prime Video**, **Pluto TV**, and **Direct Stream (MP4/HLS)** across disparate TV hardware fleets.
- **Sovereign Cinema Beam Planner (`CinemaBeamPlanner.kt`)**: Dedicated One-Job-Principle (OJP) engine that resolves canonical video IDs locally (<300ms) without cloud dependencies and generates display-waking, session-preserving ADB/DIAL launch intents customized per platform (Fire TV vs Google TV vs Roku).
- **Tactile Source Selector Chips (`CinemaBeamDialog.kt`)**: Responsive Compose chips allowing operators to switch streaming sources with tactile haptic feedback, dynamic query placeholders, and bounded modular layout ($\le 300$ lines).
- **Pre-Flight Cobalt Runtime Override (`ArgusMediaCoordinator.kt`)**: Discovered that Amazon Fire TV's Cobalt/Starboard webview engine (`dev.cobalt.app.MainActivity`) takes 2.6s–3.0s to mount the "Who's watching?" account picker on multi-account TVs. Engineered an adaptive 2.8s profile engagement pulse (`KEYCODE_DPAD_CENTER`) that automatically selects the active profile and unblocks the deep-linked stream into full 1080p playback.
- **State-Aware Idempotent Buffer Equalizer (`ArgusMediaCoordinator.kt`)**: Replaced blind `PLAY_PAUSE` toggle pulses with state-aware pauses (only if `PLAYING`) and releases (only if `PAUSED`), eliminating inverted flip-flop desynchronization.
- **Explicit Idempotent Keycode Engine (`RemoteKey.PLAY` & `RemoteKey.PAUSE`)**: Replaced toggle keycodes (`KEYCODE_MEDIA_PLAY_PAUSE = 85`) in pre-flight starting gates, slipstream auto-healing, and follower sync with explicit Android `KEYCODE_MEDIA_PLAY = 126` and `KEYCODE_MEDIA_PAUSE = 127` (and evdev hardware scancodes `KEY_PLAY = 207`, `KEY_PAUSE = 201`). Completely eliminates the toggle trap where a running stream is inadvertently paused upon starting-gate release.
- **Unified Multi-Room Parallel Volume Fanout (`ArgusMediaCoordinator.kt`)**: Expanded Watch Party transport broadcast to fan out `VOLUME_UP`, `VOLUME_DOWN`, and `MUTE` in parallel across all active room TVs simultaneously, delivering unified acoustic control from a single phone rocker tap.
- **Symmetric Google TV & Fire TV Launch Protocols (`GoogleTvProtocol.kt` & `FireTvProtocol.kt`)**: Unified ADB intent execution with DIAL fallbacks across disparate living room (Hisense Hayward MTK) and bedroom (Amazon Fire TV 4K Max Cheyne) hardware.
- **Multi-Source Adversarial Test Suite (`TvMediaBridgeAdversarialTest.kt`)**: Expanded automated tests verifying shell breakout resistance across Prime Video, Pluto TV, and direct video URL payloads.

## [1.1.0] - 2026-11-01

### Added
- **Sovereign Cinema Beam & Now Playing Bridge (`TvMediaBridge.kt`)**: Non-DRM hardware playback observation extracting live playhead timecodes and playback state from Android TV and Fire TV nodes over local network protocols.
- **Argus Media Coordinator (`ArgusMediaCoordinator.kt`)**: Reactive state orchestrator managing playhead updates, auto-reconnection, and volume telemetry.
- **Cinema Beam Dialog (`CinemaBeamDialog.kt`)**: Modular tactical modal for inspecting live movie playhead, tracking session duration, and syncing watch sessions.
- **Playback & Volume HUD Bar (`PlaybackVolumeBar.kt`)**: Real-time interactive playback controls and volume telemetry bar with haptic feedback.
- **Adversarial Test Battery (`TvMediaBridgeAdversarialTest.kt`)**: Hardened test suite validating playhead boundary calculations, corrupt JSON/telemetry payload resistance, and rapid state churn stability.

## [1.0.0] - 2026-10-13

### Added
- **Zero-Cloud Smart TV & Media Controller**: 100% local Wi-Fi control for Google TV, Android TV, Fire TV, Roku, Samsung Tizen, LG webOS, and Sony BRAVIA.
- **Multi-Protocol Discovery**: Subnet scanning over UDP multicast SSDP (`239.255.255.250:1900`), DIAL, and local TCP sweeps.
- **Tactical AMOLED D-Pad & Controls**: Power, directional navigation, volume, playback, and quick-settings keys with high-frequency haptic feedback.
- **Sovereign Device Governance**: Local network device memory, network isolation, and device forget/hide capabilities.
- **GPG Provenance Sealing**: Cryptographic release distribution signed with sovereign release key `4E428019A109578B`.
