@echo off
setlocal enableextensions enabledelayedexpansion

REM Usage: scripts\deploy_ststable_debug.cmd <TV_IP[:PORT]> [--apk <path>]

set DEVICE=%1
set APK_OVERRIDE=
if "%2"=="--apk" set APK_OVERRIDE=%3

set PKG=com.teamsmart.videomanager.tv
set ACTIVITY=com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity
set APK_DIR=smarttubetv\build\outputs\apk\ststable\debug

where adb >nul 2>nul || (
  echo adb not found in PATH
  exit /b 2
)

if "%DEVICE%"=="" (
  echo No device specified. Listing devices:
  adb devices
  echo Usage: scripts\deploy_ststable_debug.cmd ^<TV_IP[:PORT]^> [--apk ^<path^>]
  exit /b 1
)

REM Attempt to connect if looks like IP
echo %DEVICE%| findstr /r "^[0-9.][0-9.]*[:0-9]*$" >nul && adb connect %DEVICE% >nul 2>nul

for /f "usebackq tokens=*" %%A in (`adb -s %DEVICE% shell getprop ro.product.cpu.abilist`) do set ABI_LIST=%%A
if "%ABI_LIST%"=="" (
  for /f "usebackq tokens=*" %%A in (`adb -s %DEVICE% shell getprop ro.product.cpu.abi`) do set ABI_LIST=%%A
)
echo Device ABIs: %ABI_LIST%

set APK_PATH=
if not "%APK_OVERRIDE%"=="" (
  set APK_PATH=%APK_OVERRIDE%
) else (
  set ARCH=
  echo %ABI_LIST%| findstr /i "arm64-v8a" >nul && set ARCH=arm64-v8a
  if "%ARCH%"=="" (
    echo %ABI_LIST%| findstr /i "armeabi-v7a" >nul && set ARCH=armeabi-v7a
  )
  if "%ARCH%"=="" (
    echo %ABI_LIST%| findstr /i "x86" >nul && set ARCH=x86
  )
  if not "%ARCH%"=="" (
    for /f "delims=" %%F in ('dir /b /o-d "%APK_DIR%\SmartTube_stable_*_%ARCH%.apk"') do (
      set APK_PATH=%APK_DIR%\%%F
      goto :found
    )
  )
)

:found
if "%APK_PATH%"=="" (
  echo Could not locate a matching APK in %APK_DIR%
  exit /b 3
)

echo Installing: %APK_PATH%
adb -s %DEVICE% install -r -t "%APK_PATH%"

echo Launching %PKG%/%ACTIVITY%
adb -s %DEVICE% shell am start -n %PKG%/%ACTIVITY%

echo Done.
endlocal

