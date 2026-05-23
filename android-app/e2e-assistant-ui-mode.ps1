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

function InstallAndInjectPrefs($device, $videoMode) {
  & $adb -s $device install -r $apk | Write-Output
  if ($LASTEXITCODE -ne 0) { throw "adb install failed" }
  & $adb -s $device shell pm grant com.gouxiong.sleep android.permission.RECORD_AUDIO 2>$null | Out-Null
  & $adb -s $device shell pm grant com.gouxiong.sleep android.permission.CAMERA 2>$null | Out-Null
  & $adb -s $device shell pm grant com.gouxiong.sleep android.permission.POST_NOTIFICATIONS 2>$null | Out-Null
  & $adb -s $device shell settings put global window_animation_scale 0 | Out-Null
  & $adb -s $device shell settings put global transition_animation_scale 0 | Out-Null
  & $adb -s $device shell settings put global animator_duration_scale 0 | Out-Null
  & $adb -s $device shell am force-stop com.gouxiong.sleep | Out-Null
  & $adb -s $device shell am force-stop com.tencent.mm 2>$null | Out-Null

  $videoValue = if ($videoMode) { "true" } else { "false" }
  $xml = @"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="first_launch" value="false" />
    <string name="server_base_url">http://10.0.2.2:8787</string>
    <boolean name="assistant_video_chat_mode" value="$videoValue" />
    <boolean name="assistant_persona_configured" value="true" />
    <string name="assistant_name">Nuannuan</string>
    <string name="assistant_identity">caring family helper</string>
    <string name="owner_address">Nainai</string>
    <boolean name="assistant_auto_vision_enabled" value="false" />
    <boolean name="assistant_proactive_care_enabled" value="false" />
    <boolean name="hydration_reminder_enabled" value="false" />
    <boolean name="medication_enabled" value="false" />
    <boolean name="monitoring" value="false" />
    <boolean name="debug_companion_ui_test_mode" value="true" />
</map>
"@
  $tmpXml = Join-Path $env:TEMP "gouxiong_sleep_assistant_ui_prefs.xml"
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($tmpXml, $xml, $utf8NoBom)
  & $adb -s $device push $tmpXml /data/local/tmp/gouxiong_sleep_assistant_ui_prefs.xml | Out-Null
  & $adb -s $device shell chmod 644 /data/local/tmp/gouxiong_sleep_assistant_ui_prefs.xml | Out-Null
  & $adb -s $device shell run-as com.gouxiong.sleep sh -c "'mkdir -p shared_prefs && cp /data/local/tmp/gouxiong_sleep_assistant_ui_prefs.xml shared_prefs/gouxiong_sleep_prefs.xml && chmod 600 shared_prefs/gouxiong_sleep_prefs.xml'" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "run-as prefs injection failed" }
}

function ResetPrefsToLocalServer($device) {
  $xml = @"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="first_launch" value="false" />
    <string name="server_base_url">http://10.0.2.2:8787</string>
</map>
"@
  $tmpXml = Join-Path $env:TEMP "gouxiong_sleep_assistant_ui_prefs_reset.xml"
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($tmpXml, $xml, $utf8NoBom)
  & $adb -s $device shell am force-stop com.gouxiong.sleep | Out-Null
  & $adb -s $device push $tmpXml /data/local/tmp/gouxiong_sleep_assistant_ui_prefs_reset.xml | Out-Null
  & $adb -s $device shell chmod 644 /data/local/tmp/gouxiong_sleep_assistant_ui_prefs_reset.xml | Out-Null
  & $adb -s $device shell run-as com.gouxiong.sleep sh -c "'mkdir -p shared_prefs && cp /data/local/tmp/gouxiong_sleep_assistant_ui_prefs_reset.xml shared_prefs/gouxiong_sleep_prefs.xml && chmod 600 shared_prefs/gouxiong_sleep_prefs.xml'" | Out-Null
}

function DumpUi($device) {
  $remote = "/sdcard/gouxiong-assistant-ui.xml"
  $local = Join-Path $env:TEMP "gouxiong-assistant-ui.xml"
  if (Test-Path $local) { Remove-Item -LiteralPath $local -Force }
  & $adb -s $device shell rm -f $remote | Out-Null
  & $adb -s $device shell uiautomator dump --compressed $remote | Out-Null
  if ($LASTEXITCODE -ne 0) { return "" }
  & $adb -s $device pull $remote $local | Out-Null
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path $local)) { return "" }
  return Get-Content -Encoding UTF8 $local -Raw
}

function TapTextInXml($device, $raw, $text) {
  $escaped = [regex]::Escape($text)
  $pattern = "text=`"$escaped`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`""
  if ($raw -notmatch $pattern) { return $false }
  $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
  $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
  & $adb -s $device shell input tap $x $y | Out-Null
  return $true
}

function ReadAppPrefs($device) {
  $raw = & $adb -s $device shell run-as com.gouxiong.sleep cat shared_prefs/gouxiong_sleep_prefs.xml 2>$null
  if ($LASTEXITCODE -ne 0 -or -not $raw) { return "" }
  return ($raw -join "`n")
}

function PrefHasVideoMode($device, $expected) {
  $prefsRaw = ReadAppPrefs $device
  $expectedValue = if ($expected) { "true" } else { "false" }
  return $prefsRaw -match ('name="assistant_video_chat_mode" value="' + $expectedValue + '"')
}

function TapNodeByAttribute($device, $raw, $attribute, $value) {
  $escaped = [regex]::Escape($value)
  $pattern = "$attribute=`"$escaped`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`""
  if ($raw -notmatch $pattern) { return $false }
  $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
  $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
  & $adb -s $device shell input tap $x $y | Out-Null
  return $true
}

function TapModeToggle($device, $raw, $text, $contentDesc, $expectedVideoMode) {
  $tapOk = (TapNodeByAttribute $device $raw "text" $text)
  if (-not $tapOk) {
    $tapOk = (TapNodeByAttribute $device $raw "content-desc" $contentDesc)
  }
  if (-not $tapOk) { return $false }

  for ($i = 0; $i -lt 8; $i++) {
    Start-Sleep -Milliseconds 350
    if (PrefHasVideoMode $device $expectedVideoMode) { return $true }
  }

  $freshRaw = DumpUi $device
  $tapOk = (TapNodeByAttribute $device $freshRaw "content-desc" $contentDesc)
  if (-not $tapOk) {
    $tapOk = (TapNodeByAttribute $device $freshRaw "text" $text)
  }
  if (-not $tapOk) { return $false }

  for ($i = 0; $i -lt 8; $i++) {
    Start-Sleep -Milliseconds 350
    if (PrefHasVideoMode $device $expectedVideoMode) { return $true }
  }

  $lastPrefs = ReadAppPrefs $device
  throw ("Mode toggle tap did not update assistant_video_chat_mode. expected=" + $expectedVideoMode + " prefs=" + $lastPrefs)
}

function DismissInterferingDialog($device, $raw) {
  if (-not $raw) { return $false }
  if ($script:CareCompanionTitle -and $raw -match ('text="' + [regex]::Escape($script:CareCompanionTitle) + '"')) {
    if (TapTextInXml $device $raw $script:CareChatText) { return $true }
    & $adb -s $device shell input keyevent BACK | Out-Null
    OpenCompanion $device
    return $true
  }
  if ($raw -match "WeChat isn't responding") {
    return (TapTextInXml $device $raw "Close app") -or (TapTextInXml $device $raw "Wait")
  }
  if ($raw -match "isn't responding") {
    return TapTextInXml $device $raw "Wait"
  }
  return $false
}

function WaitUi($device, $predicate, $seconds, $label) {
  $deadline = [DateTime]::UtcNow.AddSeconds($seconds)
  do {
    $raw = DumpUi $device
    if (DismissInterferingDialog $device $raw) {
      Start-Sleep -Seconds 1
      continue
    }
    if (& $predicate $raw) { return $raw }
    Start-Sleep -Seconds 1
  } while ([DateTime]::UtcNow -lt $deadline)
  throw "Timed out waiting for UI: $label"
}

function Capture($device, $name) {
  $artifactDir = Join-Path $projectRoot "artifacts"
  New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
  $remote = "/sdcard/$name"
  $local = Join-Path $artifactDir $name
  & $adb -s $device shell screencap -p $remote | Out-Null
  & $adb -s $device pull $remote $local | Out-Null
  return $local
}

$TextToggle = -join ([char[]](0x6587))
$VideoToggle = -join ([char[]](0x89C6, 0x9891))
$ToTextDesc = -join ([char[]](0x5207, 0x6362, 0x5230, 0x6587, 0x5B57, 0x804A, 0x5929))
$ToVideoDesc = -join ([char[]](0x5207, 0x6362, 0x5230, 0x89C6, 0x9891, 0x804A, 0x5929))
$WaitingStatus = -join ([char[]](0x6211, 0x5728, 0x8FD9, 0x91CC, 0xFF0C, 0x60A8, 0x76F4, 0x63A5, 0x8BF4))
$MicOpened = -join ([char[]](0x6211, 0x5728, 0x542C, 0x60A8, 0x8BF4, 0x8BDD, 0xFF0C, 0x4E0D, 0x7528, 0x6309, 0x53D1, 0x9001, 0x3002))
$PauseText = -join ([char[]](0x6682, 0x505C))
$LookText = -join ([char[]](0x770B, 0x4E00, 0x773C))
$SettingsText = -join ([char[]](0x8BBE, 0x7F6E))
$CareCompanionTitle = "Nuannuan" + (-join ([char[]](0x5728, 0x53EB, 0x60A8)))
$CareChatText = -join ([char[]](0x966A, 0x6211, 0x8BF4, 0x8BF4))

function AssertVideoMode($raw) {
  if ($raw -notmatch ('text="' + [regex]::Escape($script:TextToggle) + '"')) { throw "Video mode missing compact text toggle." }
  if ($raw -notmatch ('content-desc="' + [regex]::Escape($script:ToTextDesc) + '"')) { throw "Video mode toggle accessibility label missing." }
  if ($raw -notmatch 'content-desc="Nuannuan') { throw "Video mode missing AvatarView content description." }
  if ($raw -match ('text="' + [regex]::Escape($script:VideoToggle) + '"')) { throw "Video mode must not show video-mode switch label." }
  if ($raw -match ('text="' + [regex]::Escape($script:WaitingStatus) + '"')) { throw "Video mode must not show text chat status." }
  if ($raw -match ('text="' + [regex]::Escape($script:MicOpened))) { throw "Video mode must not show text voice-status line." }
  foreach ($button in @($script:PauseText, $script:LookText, $script:SettingsText)) {
    if ($raw -notmatch ('text="' + [regex]::Escape($button) + '"')) { throw "Video mode missing action button: $button" }
  }
}

function AssertTextMode($raw) {
  if ($raw -notmatch ('text="' + [regex]::Escape($script:VideoToggle) + '"')) { throw "Text mode missing video switch button." }
  if ($raw -notmatch ('content-desc="' + [regex]::Escape($script:ToVideoDesc) + '"')) { throw "Text mode toggle accessibility label missing." }
  if ($raw -notmatch 'text="Nuannuan"') { throw "Text mode missing assistant name." }
  if ($raw -match 'content-desc="Nuannuan') { throw "Text mode must not keep the large AvatarView stage." }
  if ($raw -notmatch ('text="' + [regex]::Escape($script:WaitingStatus) + '"')) { throw "Text mode missing visible status text." }
}

function OpenCompanion($device) {
  & $adb -s $device shell input keyevent 82 | Out-Null
  & $adb -s $device shell am force-stop com.tencent.mm 2>$null | Out-Null
  & $adb -s $device shell am start -S -W -n com.gouxiong.sleep/.MainActivity -a com.gouxiong.sleep.action.OPEN_COMPANION | Out-Null
}

Write-Output "== Assistant UI mode E2E =="
$device = FirstDevice
InstallAndInjectPrefs $device $true

try {
  Write-Output "== Open video mode =="
  OpenCompanion $device
  $videoRaw = WaitUi $device { param($raw) $raw -match ('text="' + [regex]::Escape($script:TextToggle) + '"') -and $raw -match 'content-desc="Nuannuan' } 25 "assistant video mode"
  AssertVideoMode $videoRaw
  $videoShot = Capture $device "gouxiong-assistant-ui-video-mode.png"

  Write-Output "== Switch to text mode =="
  if (-not (TapModeToggle $device $videoRaw $TextToggle $ToTextDesc $false)) { throw "Could not tap compact text toggle." }
  $textRaw = WaitUi $device { param($raw) $raw -match ('text="' + [regex]::Escape($script:VideoToggle) + '"') -and $raw -match 'text="Nuannuan"' } 20 "assistant text mode"
  AssertTextMode $textRaw
  $textShot = Capture $device "gouxiong-assistant-ui-text-mode.png"

  Write-Output "== Switch back to video mode =="
  if (-not (TapModeToggle $device $textRaw $VideoToggle $ToVideoDesc $true)) { throw "Could not tap video toggle." }
  $videoAgainRaw = WaitUi $device { param($raw) $raw -match ('text="' + [regex]::Escape($script:TextToggle) + '"') -and $raw -match 'content-desc="Nuannuan' } 20 "assistant video mode after toggle back"
  AssertVideoMode $videoAgainRaw

  if (-not $KeepInjectedPrefs) {
    ResetPrefsToLocalServer $device
  }

  Write-Output "Assistant UI mode E2E passed."
  [pscustomobject]@{
    ok = $true
    device = $device
    video_screenshot = $videoShot
    text_screenshot = $textShot
    verified = @("video_mode_no_text_bubble", "text_mode_no_avatar_stage", "toggle_roundtrip")
  } | ConvertTo-Json -Depth 4 -Compress
} catch {
  if (-not $KeepInjectedPrefs) {
    try { ResetPrefsToLocalServer $device } catch {}
  }
  throw
}
