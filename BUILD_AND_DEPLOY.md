# SmartTube Build and Deploy Guide

This document provides step-by-step instructions for building and deploying SmartTube APKs.

## Prerequisites

- Android SDK installed at `C:\Users\paul\AppData\Local\Android\Sdk`
- NDK version 27.0.12077973 or compatible
- ADB enabled and accessible
- Java JDK 11 configured in gradle.properties

## Build Process

### 1. Configure NDK Path

Ensure `local.properties` contains the correct NDK path:

```properties
sdk.dir=C\\:\\Users\\paul\\AppData\\Local\\Android\\Sdk
ndk.dir=C\\:\\Users\\paul\\AppData\\Local\\Android\\Sdk\\ndk\\27.0.12077973
```

### 2. Build Commands (Stable)

#### Stable Debug/Release
```bash
# Debug (recommended for development)
./gradlew :smarttubetv:assembleStstableDebug

# Release (for distribution)
./gradlew :smarttubetv:assembleStstableRelease
```

This generates ABI‑split APKs, for example:
- `SmartTube_stable_29.22_arm64-v8a.apk` (64‑bit ARM)
- `SmartTube_stable_29.22_armeabi-v7a.apk` (32‑bit ARM)
- `SmartTube_stable_29.22_x86.apk` (Intel/x86)

#### Other Build Variants
```bash
# Beta build
./gradlew assembleStbeta

# Original build  
./gradlew assembleStorig

# List all available tasks
./gradlew tasks | grep -i assemble
```

### 3. Build Output Locations

- **Debug builds**: `./smarttubetv/build/outputs/apk/ststable/debug/`
- **Release builds**: `./smarttubetv/build/outputs/apk/ststable/release/`

## Deploy Process

### 1. One‑Command Stable Deploy (Recommended)

Use the included scripts to build and install the correct APK to all connected TVs with short timeouts:

```bash
# Linux/macOS
bash scripts/deploy_to_tvs.sh ststableDebug

# Windows PowerShell
powershell -File scripts/deploy_to_tvs.ps1 -Variant ststableDebug
```

These scripts:
- Build `StstableDebug` if needed
- Detect each device ABI and pick the matching APK
- Use short ADB timeouts and retry with force‑stop/uninstall when necessary

### 2. Connect to Android TV via ADB (Manual)

```bash
# Connect to TV (replace with your TV's IP)
adb connect 192.168.0.124

# Verify connection
adb devices
```

### 3. Install APK (Manual)

#### For Debug Builds (Pre‑signed)
```bash
adb -s 192.168.0.124:5555 install -r "./smarttubetv/build/outputs/apk/ststable/debug/SmartTube_stable_29.20_armeabi-v7a.apk"
```

#### For Release Builds (Requires Signing)
Release builds need to be signed before installation:

```bash
# Sign the APK
"C:/Users/paul/AppData/Local/Android/Sdk/build-tools/29.0.2/apksigner.bat" sign \
  --ks-key-alias androiddebugkey \
  --ks "C:/Users/paul/.android/debug.keystore" \
  --ks-pass pass:android \
  --key-pass pass:android \
  "./smarttubetv/build/outputs/apk/ststable/release/SmartTube_stable_29.20_armeabi-v7a.apk"

# Then install
adb -s 192.168.0.124:5555 install -r "./smarttubetv/build/outputs/apk/ststable/release/SmartTube_stable_29.20_armeabi-v7a.apk"
```

### 4. Architecture Selection

Choose the appropriate APK for your device:
- **arm64-v8a**: Modern Android TVs and devices (64-bit ARM)
- **armeabi-v7a**: Older Android TVs and devices (32-bit ARM) 
- **x86**: Intel-based devices or emulators

Most Android TVs use **armeabi-v7a** (32‑bit ARM).

## Troubleshooting

### NDK Version Mismatch
If you see errors about NDK version mismatch:
1. Check available NDK versions: `dir "C:\Users\paul\AppData\Local\Android\Sdk\ndk" /b`
2. Update `ndk.dir` in `local.properties` to match available version

### Certificate/Signing Issues
If installation fails with certificate errors:
- Use debug builds for development/testing
- Sign release builds with debug keystore as shown above
- For production, use proper release signing configuration

### Memory Issues
If builds fail due to memory:
- Increase paging file size (current: 200MB)
- Adjust `org.gradle.jvmargs` in `gradle.properties` if needed

## Quick Commands Reference

```bash
# Quick stable build and deploy
bash scripts/deploy_to_tvs.sh ststableDebug

# Clean build (if needed)
./gradlew clean assembleStstable
```

## Version Information

- Current version: 29.22
- Version code: 2112
- Stable applicationId: `com.teamsmart.videomanager.tv`

## Troubleshooting Gemini Features

### Manual Gemini Summary
- Access via long-press context menu → "Gemini Summary"
- Requires API key in `assets/gemini.properties` (API_KEY=...)
- Shows loading dialog while generating summary

### Auto Gemini Summary Timer Issues
If auto-summaries aren't triggering after the set delay:

1. **Check Settings**: Ensure Gemini summaries are enabled in Gemini Settings
2. **Verify Delay**: Check delay is set (3s, 5s, or 8s options)  
3. **Check Logs**: Use `adb logcat -s VideoGridFragment:D` to see debug output:
   - "Scheduling Gemini summary for: [video] with delay: [X]ms"
   - "Timer triggered - calling triggerSummary for: [video]"
   - "Gemini API configured: true/false"
   
4. **Common Issues**:
   - Rapid video selection changes cancel previous timers
   - Fragment lifecycle issues (app minimize/restore)
   - API key not configured properly

### Debug Commands
```bash
# Monitor Gemini-related logs
adb logcat -s VideoGridFragment:D | grep -i gemini

# Check if app is reading settings correctly  
adb logcat -s GeminiData:D
```

## Notes

- Debug builds are automatically signed and ready for installation
- Release builds require manual signing for installation
- The stable variant uses application ID `com.teamsmart.videomanager.tv`
- Build process generates multiple APKs for different CPU architectures
- Manual Gemini Summary always works if API key is configured
- Auto Gemini Summary may need additional debugging depending on usage patterns
