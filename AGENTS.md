# Agent Operations Guide

This doc captures repeatable workflows so the agent can deploy and verify without re-deriving steps each session.

## Deploy to TVs (ststableDebug)

Prereqs:
- Android SDK platform-tools (`adb`) in PATH
- TVs reachable via ADB over network; their addresses listed in `scripts/devices.txt`

Fast path (recommended):
1) Build APKs
   - Windows: `./gradlew.bat :smarttubetv:assembleStstableDebug`
   - Linux/macOS: `./gradlew :smarttubetv:assembleStstableDebug`
2) Deploy via script
   - Windows: `powershell -File scripts/deploy_to_tvs.ps1 -Variant ststableDebug`
   - Linux/macOS: `bash scripts/deploy_to_tvs.sh ststableDebug`

Manual fallback (if script says "No devices found"):
1) Connect devices from `scripts/devices.txt`:
   ```
   for each line in scripts/devices.txt:
     adb connect <ip:port>
   adb devices -l
   ```
2) Determine ABI and install matching APK:
   ```
   # Query ABI
   adb -s <serial> shell getprop ro.product.cpu.abi

   # APKs built at:
   smarttubetv/build/outputs/apk/ststable/debug/
     SmartTube_stable_<version>_arm64-v8a.apk
     SmartTube_stable_<version>_armeabi-v7a.apk
     SmartTube_stable_<version>_x86.apk

   # Install
   adb -s <serial> install -r -t -g <path-to-apk>
   ```

Notes:
- The deploy PS1 parses `adb devices`; in some shells it may fail. Manual fallback is reliable.
- Update `scripts/devices.txt` with TV IPs (e.g., `192.168.0.124:5555`).

