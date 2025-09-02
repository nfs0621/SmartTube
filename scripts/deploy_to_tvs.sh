#!/usr/bin/env bash
set -uo pipefail

# Build and install SmartTube to all connected Android TVs.
# Usage: scripts/deploy_to_tvs.sh [variant]
# Default variant: ststableDebug

FLAVOR_RAW="${1:-ststableDebug}"

# Parse FLAVOR_RAW into <flavor> and <buildType>
flavorPart=$(echo "$FLAVOR_RAW" | sed -E 's/(Debug|Release)$//' )
buildTypePart=$(echo "$FLAVOR_RAW" | grep -Eo '(Debug|Release)$' || echo Debug)

# Construct Gradle task variant (capitalize first char only)
TASK_FLAVOR="${flavorPart^}${buildTypePart}"
INSTALL_TASK=":smarttubetv:install${TASK_FLAVOR}"

echo "[deploy] Building APKs for variant: ${TASK_FLAVOR}"
./gradlew :smarttubetv:assemble${TASK_FLAVOR}

echo "[deploy] Detecting connected devices..."
mapfile -t SERIALS < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')

if [ ${#SERIALS[@]} -eq 0 ]; then
  echo "[deploy] No devices found. Connect your Android TV(s) via ADB and try again."
  exit 1
fi

echo "[deploy] Preparing APK selection..."
flavorDir=$(echo "$flavorPart" | tr '[:upper:]' '[:lower:]')
buildDir=$(echo "$buildTypePart" | tr '[:upper:]' '[:lower:]')
outDir="smarttubetv/build/outputs/apk/${flavorDir}/${buildDir}"
label=$(echo "$flavorDir" | sed -E 's/^st//') # stbeta -> beta
pkg="com.liskovsoft.smarttubetv"
case "$flavorDir" in
  stbeta) pkg="com.liskovsoft.smarttubetv.beta";;
  ststable) pkg="com.teamsmart.videomanager.tv";;
  storig) pkg="org.smartteam.smarttube.tv.orig";;
  strtarmenia) pkg="com.google.android.youtube.tv";;
  stredboxtv) pkg="com.redboxtv.smartyoutubetv";;
  stfiretv) pkg="com.amazon.firetv.youtube";;
  staptoide) pkg="com.teamsmart.videomanager.tv";;
esac

if [ ! -d "$outDir" ]; then
  echo "[deploy] Output dir not found: $outDir"
  exit 1
fi

echo "[deploy] Installing to ${#SERIALS[@]} device(s): ${SERIALS[*]}"
failed=()
for SERIAL in "${SERIALS[@]}"; do
  echo "[deploy] -> ${SERIAL}"
  abilist=$(adb -s "$SERIAL" shell getprop ro.product.cpu.abilist | tr -d '\r')
  abi=$(adb -s "$SERIAL" shell getprop ro.product.cpu.abi | tr -d '\r')
  if echo "$abilist" | grep -q 'arm64-v8a'; then
    abiFile="arm64-v8a"
  else
    case "$abi" in
      *armeabi-v7a*) abiFile="armeabi-v7a";;
      *x86*) abiFile="x86";;
      *) abiFile="arm64-v8a";;
    esac
  fi
  apk=$(ls -1 "$outDir"/SmartTube_*_${label}_*_${abiFile}.apk 2>/dev/null | head -n1)
  if [ -z "$apk" ]; then
    apk=$(ls -1 "$outDir"/*${abiFile}.apk 2>/dev/null | head -n1)
  fi
  if [ -z "$apk" ]; then
    echo "[deploy] No APK found for ABI $abiFile in $outDir"
    failed+=("${SERIAL}")
    continue
  fi
  echo "[deploy] Using APK: $(basename "$apk") for ABI: $abiFile"
  if ! timeout 30s adb -s "$SERIAL" install -r -t -g -d "$apk"; then
    echo "[deploy] initial install failed, trying uninstall+reinstall for ${SERIAL}"
    timeout 5s adb -s "$SERIAL" shell am force-stop "$pkg" || true
    timeout 10s adb -s "$SERIAL" uninstall "$pkg" || \
      timeout 10s adb -s "$SERIAL" shell pm uninstall -k --user 0 "$pkg" || true
    if ! timeout 30s adb -s "$SERIAL" install -t -g "$apk"; then
      echo "[deploy] adb install failed for ${SERIAL}. Will continue."
      failed+=("${SERIAL}")
    fi
  fi
done

if [ ${#failed[@]} -gt 0 ]; then
  echo "[deploy] Completed with failures on: ${failed[*]}"
  exit 2
fi

echo "[deploy] Done."
