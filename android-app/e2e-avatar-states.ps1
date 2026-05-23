param(
  [switch]$KeepInjectedPrefs
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

function EscapeXml($value) {
  return [System.Security.SecurityElement]::Escape([string]$value)
}

function InstallAndInjectPrefs($device) {
  & $adb -s $device install -r $apk | Write-Output
  if ($LASTEXITCODE -ne 0) { throw "adb install failed" }
  & $adb -s $device shell pm grant com.gouxiong.sleep android.permission.RECORD_AUDIO 2>$null | Out-Null
  & $adb -s $device shell pm grant com.gouxiong.sleep android.permission.CAMERA 2>$null | Out-Null
  & $adb -s $device shell pm grant com.gouxiong.sleep android.permission.POST_NOTIFICATIONS 2>$null | Out-Null
  & $adb -s $device shell appops set com.gouxiong.sleep CAMERA allow 2>$null | Out-Null
  & $adb -s $device shell settings put global window_animation_scale 0 | Out-Null
  & $adb -s $device shell settings put global transition_animation_scale 0 | Out-Null
  & $adb -s $device shell settings put global animator_duration_scale 0 | Out-Null
  PrepareDeviceUi $device

  $xml = @"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="first_launch" value="false" />
    <string name="server_base_url">http://10.0.2.2:8787</string>
    <boolean name="assistant_video_chat_mode" value="true" />
    <boolean name="assistant_persona_configured" value="true" />
    <string name="assistant_name">Nuannuan</string>
    <string name="assistant_identity">caring family helper</string>
    <string name="owner_address">Nainai</string>
    <boolean name="assistant_auto_vision_enabled" value="false" />
    <boolean name="assistant_proactive_care_enabled" value="false" />
    <boolean name="hydration_reminder_enabled" value="false" />
    <boolean name="medication_enabled" value="false" />
    <boolean name="monitoring" value="false" />
</map>
"@
  $tmpXml = Join-Path $env:TEMP "gouxiong_sleep_avatar_states_prefs.xml"
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($tmpXml, $xml, $utf8NoBom)
  & $adb -s $device push $tmpXml /data/local/tmp/gouxiong_sleep_avatar_states_prefs.xml | Out-Null
  & $adb -s $device shell chmod 644 /data/local/tmp/gouxiong_sleep_avatar_states_prefs.xml | Out-Null
  & $adb -s $device shell run-as com.gouxiong.sleep sh -c "'mkdir -p shared_prefs && cp /data/local/tmp/gouxiong_sleep_avatar_states_prefs.xml shared_prefs/gouxiong_sleep_prefs.xml && chmod 600 shared_prefs/gouxiong_sleep_prefs.xml'" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "run-as prefs injection failed" }
  Start-Sleep -Seconds 3
}

function PrepareDeviceUi($device) {
  foreach ($pkg in @("com.gouxiong.sleep", "com.xiaozhi.avatar.dev", "com.google.android.documentsui")) {
    & $adb -s $device shell am force-stop $pkg 2>$null | Out-Null
  }
  & $adb -s $device shell input keyevent WAKEUP | Out-Null
  & $adb -s $device shell input keyevent HOME | Out-Null
  Start-Sleep -Seconds 1
}

function ResetPrefsToLocalServer($device) {
  $xml = @"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="first_launch" value="false" />
    <string name="server_base_url">http://10.0.2.2:8787</string>
</map>
"@
  $tmpXml = Join-Path $env:TEMP "gouxiong_sleep_avatar_states_prefs_reset.xml"
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($tmpXml, $xml, $utf8NoBom)
  & $adb -s $device shell am force-stop com.gouxiong.sleep | Out-Null
  & $adb -s $device push $tmpXml /data/local/tmp/gouxiong_sleep_avatar_states_prefs_reset.xml | Out-Null
  & $adb -s $device shell chmod 644 /data/local/tmp/gouxiong_sleep_avatar_states_prefs_reset.xml | Out-Null
  & $adb -s $device shell run-as com.gouxiong.sleep sh -c "'mkdir -p shared_prefs && cp /data/local/tmp/gouxiong_sleep_avatar_states_prefs_reset.xml shared_prefs/gouxiong_sleep_prefs.xml && chmod 600 shared_prefs/gouxiong_sleep_prefs.xml'" | Out-Null
}

function ReadAppPrefs($device) {
  $oldPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    $raw = & $adb -s $device shell run-as com.gouxiong.sleep cat shared_prefs/gouxiong_sleep_prefs.xml 2>$null
    if ($LASTEXITCODE -eq 0 -and $raw) {
      return ($raw -join "`n")
    }
  } catch {
  } finally {
    $ErrorActionPreference = $oldPreference
  }
  return ""
}

function DumpUi($device, $name) {
  $remote = "/sdcard/$name.xml"
  $local = Join-Path $env:TEMP "$name.xml"
  Remove-Item -LiteralPath $local -Force -ErrorAction SilentlyContinue
  & $adb -s $device shell rm -f $remote | Out-Null
  $oldPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    & $adb -s $device shell uiautomator dump --compressed $remote 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { return "" }
    & $adb -s $device pull $remote $local 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $local)) { return "" }
  } finally {
    $ErrorActionPreference = $oldPreference
  }
  return Get-Content -Encoding UTF8 $local -Raw
}

function Capture($device, $name) {
  $artifactDir = Join-Path $projectRoot "artifacts"
  New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
  $remote = "/sdcard/$name"
  $local = Join-Path $artifactDir $name
  $oldPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    & $adb -s $device shell screencap -p $remote 2>$null | Out-Null
    & $adb -s $device pull $remote $local 2>$null | Out-Null
  } finally {
    $ErrorActionPreference = $oldPreference
  }
  if (-not (Test-Path $local)) { throw "screenshot pull failed: $name" }
  return $local
}

function WaitVisionCapture($device, $seconds) {
  for ($i = 0; $i -lt $seconds; $i++) {
    $raw = ReadAppPrefs $device
    $bytes = 0
    $width = 0
    $height = 0
    if ($raw -match 'name="last_vision_capture_bytes" value="([1-9]\d*)"') { $bytes = [int]$Matches[1] }
    if ($raw -match 'name="last_vision_capture_width" value="([1-9]\d*)"') { $width = [int]$Matches[1] }
    if ($raw -match 'name="last_vision_capture_height" value="([1-9]\d*)"') { $height = [int]$Matches[1] }
    if ($bytes -gt 0 -and $width -gt 0 -and $height -gt 0 -and $raw.Contains("debug_camera_glance")) {
      return [pscustomobject]@{
        bytes = $bytes
        width = $width
        height = $height
        raw = $raw
      }
    }
    Start-Sleep -Seconds 1
  }
  return $null
}

function WaitUrgentWakeActivity($device, $seconds) {
  for ($i = 0; $i -lt $seconds; $i++) {
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
      $activityRaw = (& $adb -s $device shell dumpsys activity activities 2>$null) -join "`n"
    } finally {
      $ErrorActionPreference = $oldPreference
    }
    $prefsRaw = ReadAppPrefs $device
    if ($activityRaw.Contains("com.gouxiong.sleep/.AlarmActivity") -and
        $prefsRaw.Contains("debug_nightmare_wake") -and
        $prefsRaw.Contains("urgent_wakeup")) {
      return $true
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

function WaitPackageStopped($device, $seconds) {
  for ($i = 0; $i -lt $seconds; $i++) {
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
      $pidText = (& $adb -s $device shell pidof com.gouxiong.sleep 2>$null) -join "`n"
    } finally {
      $ErrorActionPreference = $oldPreference
    }
    if (-not $pidText -or $pidText.Trim().Length -eq 0) { return $true }
    Start-Sleep -Seconds 1
  }
  return $false
}

function TriggerCameraGlanceWithRetry($device) {
  for ($attempt = 1; $attempt -le 2; $attempt++) {
    Write-Output "== Camera glance attempt $attempt =="
    PrepareDeviceUi $device
    [void](WaitPackageStopped $device 8)
    Start-Sleep -Seconds (2 + $attempt)
    & $adb -s $device shell am start -W -a com.gouxiong.sleep.action.DEBUG_CAMERA_GLANCE -n com.gouxiong.sleep/.MainActivity | Out-Null
    if ($LASTEXITCODE -ne 0) {
      Start-Sleep -Seconds 3
      continue
    }
    $vision = WaitVisionCapture $device 55
    if ($vision) {
      return $vision
    }
  }
  return $null
}

function TriggerUrgentWakeWithRetry($device) {
  for ($attempt = 1; $attempt -le 2; $attempt++) {
    PrepareDeviceUi $device
    [void](WaitPackageStopped $device 8)
    Start-Sleep -Seconds (2 + $attempt)
    & $adb -s $device shell am start -W -a com.gouxiong.sleep.action.DEBUG_NIGHTMARE_WAKE -n com.gouxiong.sleep/.MainActivity | Out-Null
    if ($LASTEXITCODE -ne 0) {
      Start-Sleep -Seconds 3
      continue
    }
    if (WaitUrgentWakeActivity $device 45) {
      return $true
    }
  }
  return $false
}

Write-Output "== Avatar states E2E =="
$device = FirstDevice
InstallAndInjectPrefs $device

$visionShot = ""
$urgentShot = ""
try {
  Write-Output "== Trigger camera glance state =="
  $vision = TriggerCameraGlanceWithRetry $device
  if (-not $vision) {
    $lastPrefs = ReadAppPrefs $device
    throw "Camera glance did not record last_vision_capture_* state. prefs=$lastPrefs"
  }
  $visionShot = Capture $device "gouxiong-avatar-camera-glance.png"

  Write-Output "== Trigger urgent wake avatar state =="
  if (-not (TriggerUrgentWakeWithRetry $device)) { throw "Urgent wake drill did not foreground AlarmActivity or record urgent_wakeup state." }
  $urgentShot = Capture $device "gouxiong-avatar-urgent-wakeup.png"

  if (-not $KeepInjectedPrefs) {
    ResetPrefsToLocalServer $device
  }

  Write-Output "Avatar states E2E passed."
  [pscustomobject]@{
    ok = $true
    device = $device
    camera_glance_screenshot = $visionShot
    urgent_wakeup_screenshot = $urgentShot
    verified = @("camera_glance_records_real_frame", "urgent_wakeup_alarm_activity_drill")
  } | ConvertTo-Json -Depth 4 -Compress
} catch {
  if (-not $KeepInjectedPrefs) {
    try { ResetPrefsToLocalServer $device } catch {}
  }
  throw
} finally {
  & $adb -s $device shell am force-stop com.gouxiong.sleep | Out-Null
}
