Param(
  [string[]]$Devices = @("192.168.0.90:5555", "192.168.0.124:5555"),
  [switch]$Launch
)

function Resolve-ApkForAbi($abi) {
  $base = Join-Path $PSScriptRoot "..\smarttubetv\build\outputs\apk\ststable\debug"
  switch -Regex ($abi) {
    'arm64'        { return Join-Path $base "SmartTube_stable_29.22_arm64-v8a.apk" }
    'armeabi-v7a'  { return Join-Path $base "SmartTube_stable_29.22_armeabi-v7a.apk" }
    'x86(_64)?'    { return Join-Path $base "SmartTube_stable_29.22_x86.apk" }
    default        { return Join-Path $base "SmartTube_stable_29.22_armeabi-v7a.apk" }
  }
}

Write-Host "Building ststable debug APKs..." -ForegroundColor Cyan
pushd (Join-Path $PSScriptRoot "..") | Out-Null
./gradlew.bat :smarttubetv:assembleStstableDebug -x lint
if ($LASTEXITCODE -ne 0) { Write-Error "Gradle build failed"; exit 1 }
popd | Out-Null

foreach ($dev in $Devices) {
  Write-Host "\n=== Installing to $dev ===" -ForegroundColor Green
  & adb connect $dev | Out-Null
  $abi = (& adb -s $dev shell getprop ro.product.cpu.abi).Trim()
  if (-not $abi) { Write-Warning "Could not read ABI for $dev (device offline/unauthorized?)"; continue }
  $apk = Resolve-ApkForAbi $abi
  if (-not (Test-Path $apk)) { Write-Error "APK not found: $apk"; continue }
  Write-Host "ABI=$abi APK=$apk" -ForegroundColor Yellow
  & adb -s $dev install -r "$apk"
  if ($LASTEXITCODE -ne 0) { Write-Warning "Install failed on $dev" } else { Write-Host "Install success on $dev" -ForegroundColor Green }
  if ($Launch) {
    & adb -s $dev shell monkey -p com.teamsmart.videomanager.tv -c android.intent.category.LAUNCHER 1 | Out-Null
    Write-Host "Launched on $dev"
  }
}

