# Changelog

All notable changes to **Argus (Android)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
