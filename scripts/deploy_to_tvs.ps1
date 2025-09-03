Param(
  [string]$Variant = "ststableDebug"
)

function Get-Parts($variant) {
  $m = [regex]::Match($variant, '(?<flavor>.+?)(?<buildType>Debug|Release)$')
  if ($m.Success) { return @($m.Groups['flavor'].Value, $m.Groups['buildType'].Value) }
  return @($variant, 'Debug')
}

function Invoke-WithTimeout([ScriptBlock]$Script, [int]$TimeoutSec = 30) {
  $job = Start-Job -ScriptBlock $Script
  if (-not (Wait-Job $job -Timeout $TimeoutSec)) {
    try { Stop-Job $job -Force | Out-Null } catch {}
    throw "Operation timed out after $TimeoutSec seconds"
  }
  Receive-Job $job
}

$parts = Get-Parts $Variant
$flavor = $parts[0]
$buildType = $parts[1]
$taskFlavor = ($flavor.Substring(0,1).ToUpper() + $flavor.Substring(1)) + $buildType

Write-Host "[deploy] Building APKs for variant: $taskFlavor"
./gradlew ":smarttubetv:assemble$taskFlavor"

$cfgFile = Join-Path $PSScriptRoot 'devices.txt'
if (Test-Path $cfgFile) {
  Write-Host "[deploy] Auto-connecting ADB to devices in devices.txt..."
  Get-Content $cfgFile | ForEach-Object {
    $addr = $_.Trim()
    if (-not [string]::IsNullOrWhiteSpace($addr)) {
      try { adb disconnect $addr | Out-Null } catch {}
      try { adb connect $addr | Out-Null } catch {}
    }
  }
}

Write-Host "[deploy] Detecting connected devices..."
$adbOut = adb devices
$serials = @()
foreach ($line in $adbOut.Split("`n")) {
  if ($line -match "^([\w:-]+)\s+device\s*") { $serials += $Matches[1] }
}

if ($serials.Count -eq 0) {
  Write-Error "[deploy] No devices found. Connect your Android TV(s) via ADB and try again."
  exit 1
}

$flavorDir = $flavor.ToLower()
$buildDir = $buildType.ToLower()
$outDir = "smarttubetv/build/outputs/apk/$flavorDir/$buildDir"
$label = ($flavorDir -replace '^st','')

if (-not (Test-Path $outDir)) {
  Write-Error "[deploy] Output dir not found: $outDir"
  exit 1
}

Write-Host ("[deploy] Installing to {0} device(s): {1}" -f $serials.Count, ($serials -join ", "))
$failed = @()
foreach ($s in $serials) {
  Write-Host "[deploy] -> $s"
  $abi = (adb -s $s shell getprop ro.product.cpu.abi).Trim()
  if ($abi -match 'arm64-v8a') { $abiFile = 'arm64-v8a' }
  elseif ($abi -match 'armeabi-v7a') { $abiFile = 'armeabi-v7a' }
  elseif ($abi -match 'x86') { $abiFile = 'x86' }
  else { $abiFile = 'arm64-v8a' }

  $apk = Get-ChildItem -ErrorAction SilentlyContinue "$outDir" -Filter "SmartTube_*_${label}_*_${abiFile}.apk" | Select-Object -First 1
  if (-not $apk) {
    $apk = Get-ChildItem -ErrorAction SilentlyContinue "$outDir" -Filter "*${abiFile}.apk" | Select-Object -First 1
  }
  if (-not $apk) {
    Write-Host "[deploy] No APK found for ABI $abiFile in $outDir"
    $failed += $s
    continue
  }
  Write-Host "[deploy] Using APK: $($apk.Name) for ABI: $abiFile"
  try {
    Invoke-WithTimeout { adb -s $using:s install -r -t -g "$using:apk" } 30
  } catch {
    Write-Host "[deploy] adb install failed for ${s}: $($_.Exception.Message)"
    $failed += $s
  }
}

if ($failed.Count -gt 0) {
  Write-Host "[deploy] Completed with failures on: $($failed -join ', ')"
  exit 2
}

Write-Host "[deploy] Done."
