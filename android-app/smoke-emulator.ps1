param(
  [string]$TapText = ""
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $projectRoot
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) {
  $candidates = @(
    (Join-Path $env:LOCALAPPDATA "Android\Sdk"),
    (Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk"),
    "C:\Android\Sdk",
    "D:\Android\Sdk",
    "D:\AndroidSDK"
  )
  $sdk = $candidates |
    Where-Object { $_ -and (Test-Path (Join-Path $_ "platform-tools\adb.exe")) } |
    Select-Object -First 1
}
if (-not $sdk) { throw "Android SDK not found." }

$adb = Join-Path $sdk "platform-tools\adb.exe"
$emulator = Join-Path $sdk "emulator\emulator.exe"
$apk = Join-Path $projectRoot "build\outputs\apk\gouxiong-sleep-debug.apk"
if (-not (Test-Path $apk)) {
  & powershell -ExecutionPolicy Bypass -File (Join-Path $projectRoot "build.ps1")
}
if (-not (Test-Path $apk)) { throw "APK not found: $apk" }

$out = Join-Path $repoRoot "tmp\android-smoke"
New-Item -ItemType Directory -Force -Path $out | Out-Null

& $adb start-server | Out-Null
$device = (& $adb devices | Select-String -Pattern "^emulator-\d+\s+device").Line |
  ForEach-Object { ($_ -split "\s+")[0] } |
  Select-Object -First 1

if (-not $device) {
  $avd = (& $emulator -list-avds | Select-Object -First 1)
  if (-not $avd) { throw "No Android emulator device is online and no AVD exists." }
  Start-Process -FilePath $emulator -ArgumentList @("-avd", $avd, "-no-snapshot", "-no-boot-anim", "-no-audio") -WindowStyle Hidden
  $deadline = (Get-Date).AddMinutes(3)
  do {
    Start-Sleep -Seconds 3
    $device = (& $adb devices | Select-String -Pattern "^emulator-\d+\s+device").Line |
      ForEach-Object { ($_ -split "\s+")[0] } |
      Select-Object -First 1
  } while (-not $device -and (Get-Date) -lt $deadline)
}
if (-not $device) { throw "No online emulator device found." }

$bootDeadline = (Get-Date).AddMinutes(3)
do {
  Start-Sleep -Seconds 2
  $boot = [string](& $adb -s $device shell getprop sys.boot_completed 2>$null)
  $boot = $boot.Trim()
} while ($boot -ne "1" -and (Get-Date) -lt $bootDeadline)
if ($boot -ne "1") { throw "Emulator did not finish booting: $device" }

& $adb -s $device logcat -c | Out-Null
& $adb -s $device install -r $apk | Tee-Object -FilePath (Join-Path $out "install.txt")
& $adb -s $device shell am start -n com.gouxiong.sleep/.MainActivity | Tee-Object -FilePath (Join-Path $out "start.txt")
Start-Sleep -Seconds 5
& $adb -s $device exec-out screencap -p > (Join-Path $out "home.png")
& $adb -s $device shell uiautomator dump /sdcard/gouxiong-window.xml | Out-Null
& $adb -s $device pull /sdcard/gouxiong-window.xml (Join-Path $out "home.xml") | Out-Null
& $adb -s $device logcat -d -t 1200 > (Join-Path $out "logcat.txt")

$xml = Get-Content -Raw -Path (Join-Path $out "home.xml")
if ($xml -match "Process system isn't responding|Close app|Wait") {
  throw "System/app ANR dialog is visible. See $out\home.xml and $out\logcat.txt"
}
if ($xml -notmatch "睡眠|守护|小助手|设置|睡前") {
  throw "Gouxiong first screen text not found. See $out\home.xml"
}

if ($TapText) {
  [xml]$homeXml = [System.IO.File]::ReadAllText((Join-Path $out "home.xml"), [System.Text.Encoding]::UTF8)
  $candidates = @(
    $homeXml.SelectNodes("//*[@text]") |
      Where-Object { $_.GetAttribute("text") -like "*$TapText*" }
  )
  if (-not $candidates) { throw "Tap text not found on home screen: $TapText" }

  $tapNode = $candidates |
    Where-Object { $_.GetAttribute("clickable") -eq "true" -and $_.GetAttribute("enabled") -eq "true" } |
    Select-Object -First 1
  if (-not $tapNode) { $tapNode = $candidates | Select-Object -First 1 }

  $m = [regex]::Match($tapNode.GetAttribute("bounds"), "^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$")
  if (-not $m.Success) { throw "Tap bounds not found for home screen text: $TapText" }
  $x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
  $y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
  & $adb -s $device shell input tap $x $y | Out-Null
  Start-Sleep -Seconds 2
  & $adb -s $device exec-out screencap -p > (Join-Path $out "after-tap.png")
  & $adb -s $device shell uiautomator dump /sdcard/gouxiong-window-after.xml | Out-Null
  & $adb -s $device pull /sdcard/gouxiong-window-after.xml (Join-Path $out "after-tap.xml") | Out-Null
}

Write-Host "Android smoke passed on $device"
Write-Host "Artifacts: $out"
