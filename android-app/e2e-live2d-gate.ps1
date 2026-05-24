param(
  [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
if (Get-Variable PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
  $PSNativeCommandUseErrorActionPreference = $false
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $root
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { throw "ANDROID_HOME or ANDROID_SDK_ROOT is not set." }

$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found: $adb" }

$apk = Join-Path $root "build\outputs\apk\gouxiong-sleep-debug.apk"
if (-not (Test-Path $apk)) { throw "APK missing. Run android-app\test.ps1 first: $apk" }

function FirstDevice() {
  $lines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }
  if (-not $lines -or $lines.Count -lt 1) { throw "No online Android device/emulator found." }
  return (($lines | Select-Object -First 1) -split "\s+")[0]
}

function DumpWindowXml($device, $local) {
  $remote = "/sdcard/live2d_gate_window.xml"
  & $adb -s $device shell rm -f $remote 2>$null | Out-Null
  & $adb -s $device shell uiautomator dump $remote 2>$null | Out-Null
  & $adb -s $device pull $remote $local 2>$null | Out-Null
  if (-not (Test-Path $local)) { throw "Window XML pull failed: $local" }
  return [System.IO.File]::ReadAllText((Resolve-Path $local), [System.Text.Encoding]::UTF8)
}

function DismissAnrDialog($device) {
  $remote = "/sdcard/live2d_gate_anr_check.xml"
  $local = Join-Path $env:TEMP "live2d_gate_anr_check.xml"
  & $adb -s $device shell rm -f $remote 2>$null | Out-Null
  & $adb -s $device shell uiautomator dump $remote 2>$null | Out-Null
  & $adb -s $device pull $remote $local 2>$null | Out-Null
  if (-not (Test-Path $local)) { return }
  $xml = [System.IO.File]::ReadAllText((Resolve-Path $local), [System.Text.Encoding]::UTF8)
  if ($xml -match "aerr_close|Close app|isn.?t responding") {
    & $adb -s $device shell input tap 540 1180 | Out-Null
    Start-Sleep -Seconds 2
  }
}

function InvokeAdbCapture($device, [string[]]$arguments, $outDir, $name) {
  $stdout = Join-Path $outDir "$name.stdout.txt"
  $stderr = Join-Path $outDir "$name.stderr.txt"
  $process = Start-Process -FilePath $adb -ArgumentList (@("-s", $device) + $arguments) -NoNewWindow -Wait -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
  $outText = if (Test-Path $stdout) { [System.IO.File]::ReadAllText((Resolve-Path $stdout), [System.Text.Encoding]::UTF8) } else { "" }
  $errText = if (Test-Path $stderr) { [System.IO.File]::ReadAllText((Resolve-Path $stderr), [System.Text.Encoding]::UTF8) } else { "" }
  $combined = ($outText, $errText | Where-Object { $_ }) -join "`n"
  $combined | Set-Content -Encoding UTF8 (Join-Path $outDir "$name.txt")
  return [pscustomobject]@{
    ExitCode = $process.ExitCode
    Output = $combined
  }
}

$device = FirstDevice
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outDir = Join-Path $projectRoot "artifacts\debug-ui\live2d-gate-$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $SkipInstall) {
  & $adb -s $device install -r $apk | Write-Output
  if ($LASTEXITCODE -ne 0) { throw "adb install failed" }
}

DismissAnrDialog $device
& $adb -s $device shell am force-stop com.gouxiong.sleep | Out-Null
& $adb -s $device logcat -c | Out-Null

$direct = InvokeAdbCapture $device @("shell", "am", "start", "-n", "com.gouxiong.sleep/.Live2DPreviewActivity") $outDir "direct-start"
if ($direct.Output -notmatch "SecurityException|not exported|Permission Denial") {
  throw "Direct Live2DPreviewActivity start was not denied. ExitCode=$($direct.ExitCode) Output: $($direct.Output)"
}

& $adb -s $device shell am force-stop com.gouxiong.sleep | Out-Null
DismissAnrDialog $device
$debug = InvokeAdbCapture $device @("shell", "am", "start", "-n", "com.gouxiong.sleep/.DebugLive2DEntryActivity") $outDir "debug-start"
if ($debug.ExitCode -ne 0) {
  throw "Debug Live2D entry failed to start. Output: $($debug.Output)"
}
Start-Sleep -Seconds 8

$shot = Join-Path $outDir "debug-entry.png"
& $adb -s $device shell screencap -p /sdcard/live2d_gate_debug_entry.png | Out-Null
& $adb -s $device pull /sdcard/live2d_gate_debug_entry.png $shot | Out-Null

$xml = DumpWindowXml $device (Join-Path $outDir "debug-entry.xml")
$log = (& $adb -s $device logcat -d -t 600 | Select-String -Pattern "Live2DPreview|ANR|FATAL EXCEPTION|not responding") -join "`n"
$log | Set-Content -Encoding UTF8 (Join-Path $outDir "logcat.txt")

$hasPreviewShell = $xml -match "L01 Hiyori Live2D|Tap Load to test L01|Not loaded"
$hasAnr = $xml -match "isn.?t responding|aerr_close|aerr_wait|Close app" -or $log -match "ANR|FATAL EXCEPTION|not responding"

$summary = [pscustomobject]@{
  Device = $device
  DirectStartDenied = $true
  DebugEntryVisible = $hasPreviewShell
  HasAnrOrFatal = $hasAnr
  Screenshot = $shot
  OutputDir = $outDir
}
$summary | ConvertTo-Json -Depth 4 | Tee-Object -FilePath (Join-Path $outDir "summary.json")

if (-not $hasPreviewShell) {
  throw "Debug Live2D entry did not show the gated preview shell. See $outDir"
}
if ($hasAnr) {
  throw "Debug Live2D gate produced ANR/not-responding before loading the model. See $outDir"
}

Write-Output "Live2D gate E2E passed: $outDir"
