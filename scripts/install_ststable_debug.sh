#!/usr/bin/env bash
set -euo pipefail

DEVICES=("192.168.0.90:5555" "192.168.0.124:5555")
LAUNCH=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --launch) LAUNCH=true; shift ;;
    --devices) IFS=',' read -r -a DEVICES <<< "$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

resolve_apk() {
  local abi="$1"; local base="$(dirname "$0")/../smarttubetv/build/outputs/apk/ststable/debug"
  case "$abi" in
    *arm64*) echo "$base/SmartTube_stable_29.22_arm64-v8a.apk" ;;
    *armeabi-v7a*) echo "$base/SmartTube_stable_29.22_armeabi-v7a.apk" ;;
    *x86*) echo "$base/SmartTube_stable_29.22_x86.apk" ;;
    *) echo "$base/SmartTube_stable_29.22_armeabi-v7a.apk" ;;
  esac
}

echo "Building ststable debug APKs..."
pushd "$(dirname "$0")/.." >/dev/null
./gradlew :smarttubetv:assembleStstableDebug -x lint
popd >/dev/null

for dev in "${DEVICES[@]}"; do
  echo "\n=== Installing to $dev ==="
  adb connect "$dev" >/dev/null || true
  abi="$(adb -s "$dev" shell getprop ro.product.cpu.abi | tr -d '\r')"
  if [[ -z "$abi" ]]; then echo "WARN: could not read ABI for $dev"; continue; fi
  apk="$(resolve_apk "$abi")"
  echo "ABI=$abi APK=$apk"
  adb -s "$dev" install -r "$apk" || echo "WARN: install failed on $dev"
  if $LAUNCH; then adb -s "$dev" shell monkey -p com.teamsmart.videomanager.tv -c android.intent.category.LAUNCHER 1 >/dev/null; fi
done

