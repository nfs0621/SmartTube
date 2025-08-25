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

**Important:** Only works on Android TV devices - NOT smartphones, tablets, Samsung Tizen, LG webOS, or iOS.

## Build System & Commands

### Prerequisites
- Java JDK 11 (OpenJDK 14 or older required - newer versions cause crashes)
- Android SDK with NDK 27.0.12077973
- Target Android API 33, minimum API 19

### Core Build Commands

**Always use stable builds and increment version numbers:**
```bash
# Build stable debug (recommended for development)
export JAVA_HOME="/path/to/jdk-11" && ./gradlew assembleStstableDebug -x lint

# Build stable release (requires signing)
./gradlew assembleStstableRelease

# Clean build if needed
./gradlew clean assembleStstableDebug
```

### Build Variants & Application IDs
- **ststable**: `com.teamsmart.videomanager.tv` (primary stable version)
- **stbeta**: `com.teamsmart.videomanager.tv` (beta testing)
- **storig**: `org.smartteam.smarttube.tv.orig` (original variant)
- **strtarmenia**: `com.google.android.youtube.tv` (Armenia region)

**Important:** Always use `ststable` variant for production. Never use beta for final releases.

### Architecture Selection
APKs are generated for multiple architectures:
- **armeabi-v7a**: Most Android TVs (32-bit ARM) - most common
- **arm64-v8a**: Modern Android TVs (64-bit ARM)  
- **x86**: Intel-based devices/emulators

### Version Management
Version information is in `smarttubetv/build.gradle`:
- Always increment `versionCode` and `versionName` for new builds
- Current pattern: versionCode 2111, versionName "29.21"

### Deployment
```bash
# Connect to Android TV
adb connect <TV_IP_ADDRESS>

# Install debug APK (pre-signed)
adb install -r -t "./smarttubetv/build/outputs/apk/ststable/debug/SmartTube_stable_29.21_armeabi-v7a.apk"

# Launch app
adb shell am start -n com.teamsmart.videomanager.tv/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
```

## Project Architecture

### Module Structure
SmartTube uses a multi-module Android architecture:

- **smarttubetv/**: Main TV application module (UI, activities, fragments)
- **common/**: Shared business logic, preferences, utilities
- **MediaServiceCore/**: Git submodule for YouTube API integration
- **SharedModules/**: Git submodule for shared utilities
- **exoplayer-amzn-2.10.6/**: Amazon ExoPlayer fork for video playback

### Key Components

**UI Architecture (Android TV Leanback):**
- `BrowseFragment`: Main video grid interface
- `VideoGridFragment`: Handles video selection and auto-timer for Gemini summaries
- `PlaybackFragment`: Video player with custom controls
- Various presenters handle card layouts and interactions

**Data Layer:**
- YouTube API integration via MediaServiceCore submodule
- ExoPlayer for media playback with custom extensions
- Preferences managed through `*Data` classes (e.g., `GeminiData`)

**Gemini AI Integration:**
- `GeminiClient` (common/): Handles AI summary generation with transcript fetching
- `VideoSummaryOverlay` (smarttubetv/): Displays AI summaries with navigation
- `VideoMenuPresenter`: Context menu integration
- `GeminiSettingsPresenter`: Configuration UI

### Configuration Files
- `local.properties`: SDK/NDK paths (not in git)
- `gradle.properties`: Build configuration, memory settings
- `assets/gemini.properties`: API keys (not in git)
- `BUILD_AND_DEPLOY.md`: Detailed build/deploy instructions

### Flavor-Specific Resources
Variants have separate resource folders:
- `src/ststable/`: Stable variant resources
- `src/stbeta/`: Beta variant resources
- Each contains variant-specific app icons, names, and configurations

## Gemini AI Features

### Manual Gemini Summary
- Accessible via long-press context menu ’ "Gemini Summary"
- Located at top of context menu (see `MainUIData.MENU_ITEM_DEFAULT_ORDER`)
- Requires API key in `assets/gemini.properties`
- Shows source identification: `[Detail Level: X] [Source: Y]`

### Auto Gemini Summary Timer
- Configured in `VideoGridFragment.scheduleSummary()`
- Triggers after user hovers on video for set delay (3s/5s/8s)
- Debug logging available: `adb logcat -s VideoGridFragment:D`
- Common issues: rapid navigation, lifecycle events, missing API key

### Implementation Details
- **Transcript Fetching**: Official captions ’ auto-generated fallback
- **Detail Levels**: Concise/Moderate/Detailed configurable
- **UI Navigation**: D-pad left/right/back closes summary overlay
- **Error Handling**: Graceful fallback to title/metadata if transcript unavailable

## Development Workflow

### Testing
```bash
# Monitor Gemini logs
adb logcat -s GeminiClient:D -s VideoGridFragment:D | grep -i gemini

# Check app state
adb logcat | grep -i "smarttube\|gemini\|summary"
```

### Common Issues
- **NDK Version**: Update `local.properties` to match available NDK
- **Memory**: Increase paging file if builds fail
- **Certificate**: Use debug builds for development, sign release builds
- **Gemini Timer**: Check settings enabled, API key configured, debug logs

### Code Style
- No comments unless specifically requested
- Follow existing Android TV Leanback patterns
- Use existing utilities and libraries from the codebase
- Maintain consistency with current architecture

## Important Notes

- **Security**: Never include API keys, secrets, or sensitive data in commits
- **Compatibility**: Target Android TV devices only - no phone/tablet support
- **Build Requirements**: JDK 11 is critical - newer versions cause runtime crashes
- **Git Submodules**: Run `git submodule update --init` after cloning
- **Version Control**: Always increment version numbers for new builds
- **APK Distribution**: Only distribute through official channels, never app stores