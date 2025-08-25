#!/usr/bin/env bash
set -euo pipefail

DEVICE="${1:-}"
PKG="com.teamsmart.videomanager.tv"
ACTIVITY="com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity"
APK_DIR="smarttubetv/build/outputs/apk/ststable/debug"

usage() {
  echo "Usage: $0 <TV_IP[:PORT]> [--apk <path>]" >&2
  exit 1
}

APK_OVERRIDE=""
if [[ $# -ge 2 && "$2" == "--apk" ]]; then
  APK_OVERRIDE="$3"
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH" >&2
  exit 2
fi

if [[ -z "${DEVICE}" ]]; then
  echo "No device specified. Listing devices:" >&2
  adb devices
  usage
fi

# Connect if host looks like an IP
if [[ "${DEVICE}" =~ ^[0-9.]+(:[0-9]+)?$ ]]; then
  adb connect "${DEVICE}" || true
  TARGET="${DEVICE}"
else
  TARGET="${DEVICE}"
fi

ABI_LIST=$(adb -s "${TARGET}" shell getprop ro.product.cpu.abilist 2>/dev/null | tr -d '\r')
if [[ -z "${ABI_LIST}" ]]; then
  ABI_LIST=$(adb -s "${TARGET}" shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r')
fi
echo "Device ABIs: ${ABI_LIST}"

pick_apk_for_abi() {
  local abi="$1"
  ls -1 "${APK_DIR}"/SmartTube_stable_*_"${abi}".apk 2>/dev/null | sort | tail -n1 || true
}

if [[ -n "${APK_OVERRIDE}" ]]; then
  APK_PATH="${APK_OVERRIDE}"
else
  APK_PATH=""
  if echo "${ABI_LIST}" | grep -q "arm64-v8a"; then
    APK_PATH=$(pick_apk_for_abi "arm64-v8a")
  fi
  if [[ -z "${APK_PATH}" ]] && echo "${ABI_LIST}" | grep -q "armeabi-v7a"; then
    APK_PATH=$(pick_apk_for_abi "armeabi-v7a")
  fi
  if [[ -z "${APK_PATH}" ]] && echo "${ABI_LIST}" | grep -q "x86"; then
    APK_PATH=$(pick_apk_for_abi "x86")
  fi
fi

if [[ -z "${APK_PATH}" ]]; then
  echo "Could not locate a matching APK in ${APK_DIR}" >&2
  exit 3
fi

echo "Installing: ${APK_PATH}"
adb -s "${TARGET}" install -r -t "${APK_PATH}"

echo "Launching ${PKG}/${ACTIVITY}"
adb -s "${TARGET}" shell am start -n "${PKG}/${ACTIVITY}" || true

echo "Done."

