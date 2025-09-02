# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SmartTube is a free and open-source advanced media player for Android TVs and TV boxes. It's an alternative YouTube client that provides ad-free viewing, SponsorBlock integration, 8K/HDR support, and various customization features.

**Key Features:**
- No ads, SponsorBlock integration
- 8K resolution, 60fps, HDR support
- Adjustable playback speed, live chat viewing
- Customizable buttons, no Google Services required
- Gemini AI integration for video summaries

Important: Only works on Android TV devices — NOT smartphones, tablets, Samsung Tizen, LG webOS, or iOS.

## Build System & Commands

### Prerequisites
- Java JDK 11 for the Gradle daemon (newer JDKs can build but may cause runtime issues on device). Pin JDK 11 explicitly:
  - Unix/macOS: export JAVA_HOME="/path/to/jdk-11"
  - Windows (PowerShell): $env:JAVA_HOME="C:\\Java\\jdk-11"
  - Windows (CMD): setx JAVA_HOME "C:\\Java\\jdk-11"
  - Alternatively set in Gradle: add `org.gradle.java.home=/path/to/jdk-11` to `gradle.properties`.
- Android SDK with NDK 27.0.12077973
- Compile/Target SDK: defined via project properties (currently targeting SDK 34 in this repo); min SDK 19

### Core Build Commands

Always use stable builds for production and increment version numbers in `smarttubetv/build.gradle`.

- Unix/macOS:
  - Build stable debug (recommended for development): `./gradlew :smarttubetv:assembleStstableDebug -x lint`
  - Install stable debug directly: `./gradlew :smarttubetv:installStstableDebug`
  - Build stable release (requires signing): `./gradlew :smarttubetv:assembleStstableRelease`
  - Clean + build: `./gradlew clean :smarttubetv:assembleStstableDebug`
  - Lint check: `./gradlew lint`
  - Check dependencies: `./gradlew :smarttubetv:dependencies`
- Windows:
  - `gradlew.bat :smarttubetv:assembleStstableDebug -x lint`
  - `gradlew.bat :smarttubetv:installStstableDebug`
  - `gradlew.bat :smarttubetv:assembleStstableRelease`
  - `gradlew.bat lint`

### Build Variants & Application IDs
- ststable: `com.teamsmart.videomanager.tv` (primary stable version)
- stbeta: `com.teamsmart.videomanager.tv.beta` (applicationIdSuffix ".beta")
- storig: `org.smartteam.smarttube.tv.orig` (original variant)
- strtarmenia: `com.google.android.youtube.tv` (Armenia region)

Important: Use the ststable variant for production.

### Architecture Selection
APKs are generated for multiple architectures:
- armeabi-v7a: Most Android TVs (32-bit ARM) — most common
- arm64-v8a: Modern Android TVs (64-bit ARM)
- x86: Intel-based devices/emulators

Detect device ABI and pick the matching APK:
```bash
adb shell getprop ro.product.cpu.abilist  # e.g., "arm64-v8a,armeabi-v7a"
```

### Version Management
Version information is in `smarttubetv/build.gradle`:
- Always increment `versionCode` and `versionName` for new builds
- APKs follow the pattern: `SmartTube_<flavor>_<version>_<abi>.apk` (see `smarttubetv/build/outputs/apk/.../output.json` for the exact names)

### Deployment
```bash
# First-time wireless ADB (from a USB-connected session)
adb tcpip 5555 && adb connect <TV_IP_ADDRESS>:5555

# Install stable debug (Unix/macOS)
./gradlew :smarttubetv:installStstableDebug

# Or install a specific ABI APK
adb install -r -t smarttubetv/build/outputs/apk/ststable/debug/SmartTube_stable_<version>_<abi>.apk

# Launch app
adb shell am start -n com.teamsmart.videomanager.tv/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity

# Verify it is running
adb shell ps | grep com.teamsmart.videomanager.tv
```

Helper scripts are available in `scripts/`:
- Unix/macOS: `scripts/deploy_ststable_debug.sh <TV_IP[:PORT]>`
- Windows CMD: `scripts\deploy_ststable_debug.cmd <TV_IP[:PORT]>`

## Project Architecture

### Module Structure
SmartTube uses a multi-module Android architecture:

- smarttubetv/: Main TV application module (UI, activities, fragments)
- common/: Shared business logic, preferences, utilities
- MediaServiceCore/: Git submodule for YouTube API integration
- SharedModules/: Git submodule for shared utilities
- exoplayer-amzn-2.10.6/: Amazon ExoPlayer fork for video playback (library + extensions)
- Additional local modules observed in builds: `fragment-1.1.0`, `leanback-1.0.0`, `chatkit`, `sharedutils`, `commons-io-2.8.0`, `leanbackassistant`, `mediaserviceinterfaces`, `youtubeapi`.

### Key Components

UI Architecture (Android TV Leanback):
- `BrowseFragment`: Main video grid interface
- `VideoGridFragment`: Handles video selection and auto-timer for Gemini summaries
- `PlaybackFragment`: Video player with custom controls
- Presenters handle card layouts and interactions

Data Layer:
- YouTube API integration via MediaServiceCore submodule
- ExoPlayer for media playback with custom extensions
- Preferences managed through `*Data` classes (e.g., `GeminiData`)

Gemini AI Integration:
- `GeminiClient` (common/): Handles AI summary generation with transcript fetching
- `VideoSummaryOverlay` (smarttubetv/): Displays AI summaries with navigation
- `VideoMenuPresenter`: Context menu integration
- `GeminiSettingsPresenter`: Configuration UI

### Configuration Files
- `local.properties`: SDK/NDK paths (not in git). Example:
  ```
  sdk.dir=/path/to/Android/Sdk
  ndk.dir=/path/to/Android/Sdk/ndk/27.0.12077973
  ```
- `gradle.properties`: Build configuration, memory settings. Consider pinning JDK:
  ```
  org.gradle.java.home=/path/to/jdk-11
  org.gradle.jvmargs=-Xmx2g
  ```
- `smarttubetv/src/main/assets/gemini.properties`: API keys (not in git). See `gemini.properties.example`.
- `BUILD_AND_DEPLOY.md`: Detailed build/deploy instructions

### Flavor-Specific Resources
Variants have separate resource folders:
- `src/ststable/`: Stable variant resources
- `src/stbeta/`: Beta variant resources
- Each contains variant-specific app icons, names, and configurations

## Gemini AI Features

### Manual Gemini Summary
- Accessible via long-press context menu → "Gemini Summary" (top entry per `MainUIData.MENU_ITEM_DEFAULT_ORDER`).
- Requires API key in `assets/gemini.properties`.
- Header shows: `[Detail Level: X] [Source: Y] [Official CC Available: Yes/No]`.
- Overlay controls: `OK` marks watched, `Up/Down` scroll, `Left/Right/Back` close.

### Auto Gemini Summary Timer
- Configured in `VideoGridFragment.scheduleSummary()`.
- Triggers after user hovers on a video for the set delay (3s/5s/8s).
- Overlay controls identical to manual summary (OK marks watched, etc.).
- Debug logging: `adb logcat -s VideoGridFragment:D`.
- Common issues: rapid navigation, lifecycle events, missing API key.

### Implementation Details
- Transcript pipeline (best → fallback):
  1) InnerTube `get_transcript` (robust, no scraping)
  2) Player captions via `VideoInfoApi` (prefer Official EN → Auto EN → first available)
  3) Watch page `captionTracks` (consent bypass, EN locale)
  4) `timedtext` endpoint (`fmt=vtt`/`json3`/XML)
- Preferred transcript language: English by default; requests EN translations where possible (`tlang=en`).
- Header shows whether an official track exists even if a fallback is used.
- Detail Levels: Concise / Moderate / Detailed.
- Overlay controls: `OK` marks watched; `Up/Down` scroll; `Left/Right/Back` close.
- Long videos: Increased timeouts and automatic chunked summarization for very large transcripts (summarize chunks, then combine).
- Error handling: Graceful fallback to title/metadata if transcript unavailable.

### Gemini Settings (in‑app)
- Enable Summaries: toggle on/off.
- Summary Delay: 3s / 5s / 8s.
- Detail Level: Concise / Moderate / Detailed.
- Transcript Length: Full transcript (default) / ~4k chars / ~12k chars.
- Preferred Transcript Language: English.
- Verbose Logging: off by default; enable for detailed diagnostics.

### Mark as Watched (overlay)
- Press `OK` (DPAD Center/Enter) on the summary overlay to mark the video as watched.
- This updates history (`updateHistory(video, 0)`), marks fully viewed, saves/persists state, and syncs the playlist.
- A short toast confirms the action.

## Development Workflow

### Testing & Debugging
```bash
# Monitor Gemini logs
adb logcat -s GeminiClient:D -s VideoGridFragment:D | grep -i gemini

# Check app state
adb logcat | grep -i "smarttube\|gemini\|summary"

# Run unit tests (ExoPlayer components)
./gradlew test

# Test specific module
./gradlew :common:test
```

### Common Issues
- Wrong JDK: Ensure Gradle uses JDK 11 (set `JAVA_HOME` or `org.gradle.java.home`).
- NDK Version: Update `local.properties` to match available NDK (27.0.12077973).
- Memory: Increase Gradle heap via `org.gradle.jvmargs` or system paging file if builds fail.
- Certificate: Use debug builds for development; sign release builds with your keystore.
- Gemini Timer: Check settings enabled, API key configured, debug logs.

### Release Signing
Configure your keystore and run a release build:
```properties
# gradle.properties (do not commit secrets)
RELEASE_STORE_FILE=/abs/path/to/keystore.jks
RELEASE_STORE_PASSWORD=****
RELEASE_KEY_ALIAS=smarttube
RELEASE_KEY_PASSWORD=****
```
Then wire these into `signingConfigs` in `smarttubetv/build.gradle` and run:
```bash
./gradlew :smarttubetv:assembleStstableRelease
```

### Code Style
- No comments unless specifically requested
- Follow existing Android TV Leanback patterns
- Use existing utilities and libraries from the codebase
- Maintain consistency with current architecture

## Git Submodules

This project uses git submodules for shared components:
- `MediaServiceCore/`: YouTube API integration
- `SharedModules/`: Common utilities and libraries
- `exoplayer-amzn-2.10.6/`: Amazon ExoPlayer fork

After cloning: `git submodule update --init --recursive`

## Important Notes

- Security: Never include API keys, secrets, or sensitive data in commits.
- Compatibility: Android TV devices only — no phone/tablet/webOS/Tizen/iOS support.
- Build Requirements: Prefer JDK 11 for Gradle daemon.
- Version Control: Always increment version numbers for new builds.
- APK Distribution: Only distribute through official channels, never app stores.
