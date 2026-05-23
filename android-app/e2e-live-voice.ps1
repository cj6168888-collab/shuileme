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

function JsonBody($value) {
  return ($value | ConvertTo-Json -Depth 8 -Compress)
}

function PostJson($base, $path, $body, $token = "") {
  $headers = @{}
  if ($token) { $headers.Authorization = "Bearer $token" }
  return Invoke-RestMethod -Method Post -Uri ($base + $path) -ContentType "application/json; charset=utf-8" -Headers $headers -Body (JsonBody $body) -TimeoutSec 60
}

function GetJson($base, $path, $token = "") {
  $headers = @{}
  if ($token) { $headers.Authorization = "Bearer $token" }
  return Invoke-RestMethod -Method Get -Uri ($base + $path) -Headers $headers -TimeoutSec 30
}

function FirstDevice() {
  $lines = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice$" }
  if (-not $lines -or $lines.Count -lt 1) { throw "No online Android device/emulator found." }
  return (($lines | Select-Object -First 1) -split "\s+")[0]
}

function WaitHealth($base) {
  for ($i = 0; $i -lt 40; $i++) {
    try {
      $health = GetJson $base "/health"
      if ($health.ok) { return $health }
    } catch {
      Start-Sleep -Milliseconds 500
    }
  }
  throw "E2E server did not become healthy: $base"
}

function WaitPort($port) {
  for ($i = 0; $i -lt 40; $i++) {
    $busy = netstat -ano | Select-String ":$port" | Select-String "LISTENING"
    if ($busy) { return }
    Start-Sleep -Milliseconds 250
  }
  throw "Port $port did not start listening."
}

function StopE2EPort($port) {
  if (-not $port) { return }
  $lines = netstat -ano | Select-String ":$port" | Select-String "LISTENING"
  foreach ($line in $lines) {
    if ($line.ToString() -match "\s+(\d+)\s*$") {
      $processId = [int]$Matches[1]
      $process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
      if ($process -and $process.CommandLine -match "gouxiong-server\.mjs") {
        Stop-Process -Id $processId -Force
      }
    }
  }
}

function ResetTempDataDir($path, $expectedLeaf) {
  $tempRoot = [System.IO.Path]::GetFullPath($env:TEMP).TrimEnd('\')
  $fullPath = [System.IO.Path]::GetFullPath($path).TrimEnd('\')
  $leaf = Split-Path -Leaf $fullPath
  if ($leaf -ne $expectedLeaf -or -not $fullPath.StartsWith($tempRoot + "\", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to reset unexpected E2E data directory: $fullPath"
  }
  if (Test-Path -LiteralPath $fullPath) {
    Remove-Item -LiteralPath $fullPath -Recurse -Force
  }
  New-Item -ItemType Directory -Force -Path $fullPath | Out-Null
}

function EscapeXml($value) {
  return [System.Security.SecurityElement]::Escape([string]$value)
}

function GrantRuntimePermissions($device) {
  foreach ($permission in @(
      "android.permission.RECORD_AUDIO",
      "android.permission.CAMERA",
      "android.permission.READ_MEDIA_AUDIO",
      "android.permission.READ_MEDIA_IMAGES",
      "android.permission.READ_MEDIA_VIDEO",
      "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
      "android.permission.POST_NOTIFICATIONS",
      "android.permission.BLUETOOTH_CONNECT",
      "android.permission.CALL_PHONE",
      "android.permission.SEND_SMS"
    )) {
    & $adb -s $device shell pm grant com.gouxiong.sleep $permission 2>$null | Out-Null
  }
  foreach ($op in @("CAMERA", "RECORD_AUDIO", "BLUETOOTH_CONNECT", "CALL_PHONE", "SEND_SMS")) {
    & $adb -s $device shell appops set com.gouxiong.sleep $op allow 2>$null | Out-Null
  }
}

function IsPermissionControllerForeground($device) {
  $oldPreference = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    $activity = (& $adb -s $device shell dumpsys activity activities 2>$null | Select-String "topResumedActivity|mResumedActivity|GrantPermissionsActivity") -join "`n"
    return ($activity -match "com\.android\.permissioncontroller" -and $activity -match "GrantPermissionsActivity")
  } finally {
    $ErrorActionPreference = $oldPreference
  }
}

function DismissPermissionDialogs($device) {
  $dismissed = $false
  for ($i = 0; $i -lt 10; $i++) {
    if (-not (IsPermissionControllerForeground $device)) {
      break
    }
    $dumpPath = "/sdcard/gouxiong_sleep_e2e_window.xml"
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
      $dumpOutput = (& $adb -s $device shell uiautomator dump $dumpPath 2>&1) -join "`n"
      if ($LASTEXITCODE -ne 0 -or $dumpOutput.Contains("could not get idle state")) {
        Start-Sleep -Milliseconds 500
        continue
      }
      $xml = (& $adb -s $device shell cat $dumpPath 2>$null) -join "`n"
    } finally {
      $ErrorActionPreference = $oldPreference
    }
    if (-not $xml -or -not $xml.Contains("com.android.permissioncontroller")) {
      break
    }
    if ($xml -match 'resource-id="com\.android\.permissioncontroller:id/permission_allow_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
      $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
      $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
      & $adb -s $device shell input tap $x $y | Out-Null
      $dismissed = $true
      Start-Sleep -Milliseconds 700
      continue
    }
    break
  }
  return $dismissed
}

function InjectPrefs($device, $serverBaseForEmulator, $phone, $token, $userId) {
  & $adb -s $device install -r $apk | Write-Output
  if ($LASTEXITCODE -ne 0) { throw "adb install failed" }
  GrantRuntimePermissions $device
  & $adb -s $device shell am force-stop com.gouxiong.sleep | Out-Null

  $xml = @"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="first_launch" value="false" />
    <string name="server_base_url">$(EscapeXml $serverBaseForEmulator)</string>
    <string name="server_phone">$(EscapeXml $phone)</string>
    <string name="server_auth_token">$(EscapeXml $token)</string>
    <int name="server_user_id" value="$userId" />
    <boolean name="assistant_persona_configured" value="true" />
    <string name="assistant_name">Nuannuan</string>
    <string name="assistant_identity">caring family helper</string>
    <string name="owner_address">Nainai</string>
    <string name="owner_health_profile">high blood pressure, occasional dizziness</string>
    <string name="owner_medication_habits">morning blood-pressure medicine as prescribed</string>
    <string name="owner_sleep_situation">wakes at night, occasional snoring</string>
    <string name="owner_family_situation">daughter lives nearby</string>
    <string name="owner_hobbies">walking and opera</string>
    <string name="owner_care_preference">short, warm, direct voice reminders</string>
    <boolean name="assistant_proactive_care_enabled" value="true" />
    <boolean name="monitoring" value="false" />
</map>
"@
  $tmpXml = Join-Path $env:TEMP "gouxiong_sleep_voice_prefs.xml"
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($tmpXml, $xml, $utf8NoBom)
  & $adb -s $device push $tmpXml /data/local/tmp/gouxiong_sleep_voice_prefs.xml | Out-Null
  & $adb -s $device shell chmod 644 /data/local/tmp/gouxiong_sleep_voice_prefs.xml | Out-Null
  & $adb -s $device shell run-as com.gouxiong.sleep sh -c "'mkdir -p shared_prefs && cp /data/local/tmp/gouxiong_sleep_voice_prefs.xml shared_prefs/gouxiong_sleep_prefs.xml && chmod 600 shared_prefs/gouxiong_sleep_prefs.xml'" | Out-Null
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
  $tmpXml = Join-Path $env:TEMP "gouxiong_sleep_voice_prefs_reset.xml"
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($tmpXml, $xml, $utf8NoBom)
  & $adb -s $device shell am force-stop com.gouxiong.sleep | Out-Null
  & $adb -s $device push $tmpXml /data/local/tmp/gouxiong_sleep_voice_prefs_reset.xml | Out-Null
  & $adb -s $device shell chmod 644 /data/local/tmp/gouxiong_sleep_voice_prefs_reset.xml | Out-Null
  & $adb -s $device shell run-as com.gouxiong.sleep sh -c "'mkdir -p shared_prefs && cp /data/local/tmp/gouxiong_sleep_voice_prefs_reset.xml shared_prefs/gouxiong_sleep_prefs.xml && chmod 600 shared_prefs/gouxiong_sleep_prefs.xml'" | Out-Null
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

function WaitLiveVoiceReply($device, $seconds, $titleText, $bodyText) {
  for ($i = 0; $i -lt $seconds; $i++) {
    $raw = ReadAppPrefs $device
    if ($raw.Contains("last_voice_state_stage") -and $raw.Contains("reply") -and $raw.Contains($titleText) -and $raw.Contains($bodyText)) {
      return $true
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

function TriggerDebugVoiceText($device, $utterance) {
  & $adb -s $device shell am start -W -a com.gouxiong.sleep.action.DEBUG_VOICE_TEXT --es debug_voice_text "$utterance" -n com.gouxiong.sleep/.MainActivity | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "debug voice intent failed: $utterance" }
  if (DismissPermissionDialogs $device) {
    GrantRuntimePermissions $device
    & $adb -s $device shell am start -W -a com.gouxiong.sleep.action.DEBUG_VOICE_TEXT --es debug_voice_text "$utterance" -n com.gouxiong.sleep/.MainActivity | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "debug voice intent failed after permission grant: $utterance" }
    DismissPermissionDialogs $device | Out-Null
  }
}

function AssertVoiceShortcutReply($device, $utterance, $titleText, $bodyText, $seconds = 20) {
  TriggerDebugVoiceText $device $utterance
  if (-not (WaitLiveVoiceReply $device $seconds $titleText $bodyText)) {
    throw "Voice shortcut did not route to expected reply: $utterance -> $titleText / $bodyText"
  }
}

function WaitCompanionShortcutRoute($device, $seconds, $routeText, $sourceText = "") {
  for ($i = 0; $i -lt $seconds; $i++) {
    $raw = ReadAppPrefs $device
    if ($raw.Contains("last_companion_shortcut_route") -and $raw.Contains($routeText) -and
        ($sourceText.Length -eq 0 -or $raw.Contains($sourceText))) {
      return $true
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

function AssertVoiceShortcutRoute($device, $utterance, $routeText, $seconds = 20) {
  TriggerDebugVoiceText $device $utterance
  if (-not (WaitCompanionShortcutRoute $device $seconds $routeText $utterance)) {
    throw "Voice shortcut did not record expected route: $utterance -> $routeText"
  }
}

function TriggerDebugSleepCheck($device) {
  & $adb -s $device shell am start -W -a com.gouxiong.sleep.action.DEBUG_SLEEP_CHECK -n com.gouxiong.sleep/.MainActivity | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "debug sleep check intent failed" }
  if (DismissPermissionDialogs $device) {
    GrantRuntimePermissions $device
    & $adb -s $device shell am start -W -a com.gouxiong.sleep.action.DEBUG_SLEEP_CHECK -n com.gouxiong.sleep/.MainActivity | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "debug sleep check intent failed after permission grant" }
    DismissPermissionDialogs $device | Out-Null
  }
}

function TriggerDebugSleepCheckTimeout($device) {
  & $adb -s $device shell am start -W -a com.gouxiong.sleep.action.DEBUG_SLEEP_CHECK_TIMEOUT -n com.gouxiong.sleep/.MainActivity | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "debug sleep check timeout intent failed" }
  if (DismissPermissionDialogs $device) {
    GrantRuntimePermissions $device
    & $adb -s $device shell am start -W -a com.gouxiong.sleep.action.DEBUG_SLEEP_CHECK_TIMEOUT -n com.gouxiong.sleep/.MainActivity | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "debug sleep check timeout intent failed after permission grant" }
    DismissPermissionDialogs $device | Out-Null
  }
}

function WaitLiveVoiceStage($device, $seconds, $stageText, $titleText, $bodyText = "") {
  for ($i = 0; $i -lt $seconds; $i++) {
    $raw = ReadAppPrefs $device
    if (($raw -match '<string name="last_voice_state_stage">([^<]*)</string>') -and
        $Matches[1] -eq $stageText -and
        ($titleText.Length -eq 0 -or $raw.Contains($titleText)) -and
        ($bodyText.Length -eq 0 -or $raw.Contains($bodyText))) {
      return $true
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

function LatestLiveVoiceStage($device) {
  $raw = ReadAppPrefs $device
  if ($raw -match '<string name="last_voice_state_stage">([^<]*)</string>') {
    return $Matches[1]
  }
  return ""
}

function WaitLiveAudioFrames($device, $seconds) {
  for ($i = 0; $i -lt $seconds; $i++) {
    $raw = ReadAppPrefs $device
    if ($raw -match 'name="last_live_audio_frame_count" value="([1-9]\d*)"') {
      return [int]$Matches[1]
    }
    Start-Sleep -Seconds 1
  }
  return 0
}

function WaitLiveTtsDeltas($device, $seconds) {
  for ($i = 0; $i -lt $seconds; $i++) {
    $raw = ReadAppPrefs $device
    if ($raw -match 'name="last_live_tts_delta_count" value="([1-9]\d*)"') {
      return [int]$Matches[1]
    }
    Start-Sleep -Seconds 1
  }
  return 0
}

function WaitLiveEmotionTag($device, $seconds, $requiredSource = "") {
  for ($i = 0; $i -lt $seconds; $i++) {
    $raw = ReadAppPrefs $device
    if (($raw -match 'name="last_live_emotion_tag_count" value="([1-9]\d*)"') -and
        $raw.Contains("last_live_emotion_tag_emotion") -and
        $raw.Contains("last_live_emotion_tag_intensity") -and
        $raw.Contains("last_live_emotion_tag_gesture") -and
        $raw.Contains("last_live_emotion_tag_safety_level") -and
        ($requiredSource.Length -eq 0 -or $raw.Contains($requiredSource))) {
      return [int]$Matches[1]
    }
    Start-Sleep -Seconds 1
  }
  return 0
}

function WaitLiveModelAudioFrames($device, $seconds) {
  for ($i = 0; $i -lt $seconds; $i++) {
    $raw = ReadAppPrefs $device
    if (($raw -match 'name="last_live_model_audio_frame_count" value="([1-9]\d*)"') -and $raw.Contains("last_live_model_audio_frame_source") -and $raw.Contains("played")) {
      return [int]$Matches[1]
    }
    Start-Sleep -Seconds 1
  }
  return 0
}

function WaitLiveVoiceInterrupted($device, $seconds) {
  for ($i = 0; $i -lt $seconds; $i++) {
    $raw = ReadAppPrefs $device
    if (($raw -match 'name="last_live_abort_count" value="([1-9]\d*)"') -and
        ($raw -match 'name="last_live_abort_realtime_aborted" value="true"') -and
        $raw.Contains("last_live_abort_realtime_source") -and
        ($raw.Contains("server_realtime_bridge_abort") -or $raw.Contains("server_tts_interrupted"))) {
      return $true
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

function WaitLiveAutoBargeIn($device, $seconds) {
  for ($i = 0; $i -lt $seconds; $i++) {
    $raw = ReadAppPrefs $device
    if (($raw -match 'name="last_live_auto_barge_in_count" value="([1-9]\d*)"') -and
        ($raw -match 'name="last_live_auto_barge_in_frames" value="([1-9]\d*)"') -and
        ($raw -match 'name="last_live_auto_barge_in_speech_ms" value="2[0-9][0-9]"') -and
        $raw.Contains("last_live_auto_barge_in_threshold_rms") -and
        $raw.Contains("last_live_auto_barge_in_noise_floor_rms")) {
      return $true
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

function WaitLogContains($path, $pattern, $seconds) {
  for ($i = 0; $i -lt $seconds; $i++) {
    if ((Test-Path -LiteralPath $path) -and (Select-String -LiteralPath $path -Pattern $pattern -Quiet)) {
      return $true
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

function TriggerLiveAutoBargeIn($device) {
  & $adb -s $device shell am start -W --activity-reorder-to-front -a com.gouxiong.sleep.action.DEBUG_LIVE_BARGE_IN -n com.gouxiong.sleep/.MainActivity | Out-Null
}

function TapLiveCompanionStage($device) {
  & $adb -s $device shell am start -W --activity-reorder-to-front -n com.gouxiong.sleep/.MainActivity | Out-Null
  Start-Sleep -Milliseconds 600
  $sizeText = (& $adb -s $device shell wm size 2>$null) -join "`n"
  $width = 1080
  $height = 2400
  if ($sizeText -match "(\d+)x(\d+)") {
    $width = [int]$Matches[1]
    $height = [int]$Matches[2]
  }
  $x = [int]($width / 2)
  $y = [int]($height * 0.62)
  & $adb -s $device shell input tap $x $y | Out-Null
}

function WaitServerChatPersistence($base, $token, $seconds) {
  $last = $null
  for ($i = 0; $i -lt $seconds; $i++) {
    $last = GetJson $base "/api/me/export" $token
    $allChat = ($last.chat_messages | ConvertTo-Json -Depth 6)
    if ($allChat -match "I_woke_up_twice" -and $last.chat_messages.Count -ge 2) {
      return $last
    }
    Start-Sleep -Seconds 1
  }
  return $last
}

function DumpUi($device) {
  $localDump = Join-Path $env:TEMP "gouxiong-voice-window.xml"
  Remove-Item -LiteralPath $localDump -Force -ErrorAction SilentlyContinue
  for ($attempt = 0; $attempt -lt 6; $attempt++) {
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
      & $adb -s $device shell uiautomator dump /sdcard/gouxiong-voice-window.xml 2>$null | Out-Null
      $dumpExit = $LASTEXITCODE
    } catch {
      $dumpExit = 1
    } finally {
      $ErrorActionPreference = $oldPreference
    }
    if ($dumpExit -ne 0) {
      Start-Sleep -Milliseconds 800
      continue
    }
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
      & $adb -s $device pull /sdcard/gouxiong-voice-window.xml $localDump 2>$null | Out-Null
      $pullExit = $LASTEXITCODE
    } catch {
      $pullExit = 1
    } finally {
      $ErrorActionPreference = $oldPreference
    }
    if ($pullExit -ne 0) {
      Start-Sleep -Milliseconds 800
      continue
    }
    if ((Test-Path $localDump) -and ((Get-Item $localDump).Length -gt 0)) {
      return Get-Content -Encoding UTF8 $localDump -Raw
    }
    Start-Sleep -Milliseconds 800
  }
  return ""
}

function TapIfText($device, $raw, $text) {
  $escaped = [regex]::Escape($text)
  $pattern = "text=`"$escaped`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`""
  if ($raw -notmatch $pattern) { return $false }
  $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
  $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
  & $adb -s $device shell input tap $x $y | Out-Null
  return $true
}

function WaitUiContains($device, $patterns, $seconds) {
  for ($i = 0; $i -lt $seconds; $i++) {
    $raw = DumpUi $device
    if (-not $raw) {
      Start-Sleep -Seconds 1
      continue
    }
    if ($raw -match "WeChat isn't responding") {
      [void](TapIfText $device $raw "Close app")
      Start-Sleep -Seconds 1
      continue
    }
    foreach ($pattern in $patterns) {
      if ($raw.Contains($pattern)) { return $true }
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

$portCandidates = 8812..8822
$port = $null
foreach ($candidate in $portCandidates) {
  $busy = netstat -ano | Select-String ":$candidate" | Select-String "LISTENING"
  if (-not $busy) { $port = $candidate; break }
}
if (-not $port) { throw "No free E2E port found in $($portCandidates -join ', ')" }

$realtimePort = $null
foreach ($candidate in 8830..8845) {
  $busy = netstat -ano | Select-String ":$candidate" | Select-String "LISTENING"
  if (-not $busy) { $realtimePort = $candidate; break }
}
if (-not $realtimePort) { throw "No free fake realtime port found." }

$dataLeaf = "gouxiong-sleep-voice-e2e-data-$port"
$dataDir = Join-Path $env:TEMP $dataLeaf
$dbPath = Join-Path $dataDir "e2e.sqlite3"
ResetTempDataDir $dataDir $dataLeaf
$runLog = Join-Path $env:TEMP "gouxiong-sleep-voice-e2e-$port.run.log"
Set-Content -Path $runLog -Value "APK live voice E2E run started at $(Get-Date -Format o)" -Encoding UTF8

function Log($message) {
  $line = "$(Get-Date -Format HH:mm:ss) $message"
  Add-Content -Path $script:runLog -Value $line -Encoding UTF8
  Write-Output $message
}

$serverScript = Join-Path $projectRoot "server\gouxiong-server.mjs"
$serverLog = Join-Path $env:TEMP "gouxiong-sleep-voice-e2e-$port.log"
$serverErr = Join-Path $env:TEMP "gouxiong-sleep-voice-e2e-$port.err.log"
$fakeRealtimeScript = Join-Path $env:TEMP "gouxiong-fake-realtime-$realtimePort.mjs"
$fakeRealtimeLog = Join-Path $env:TEMP "gouxiong-fake-realtime-$realtimePort.log"
$fakeRealtimeErr = Join-Path $env:TEMP "gouxiong-fake-realtime-$realtimePort.err.log"
Set-Content -Encoding UTF8 -Path $fakeRealtimeScript -Value @'
import http from 'node:http';
import crypto from 'node:crypto';
const port = Number(process.argv[2]);
const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';
function accept(key) { return crypto.createHash('sha1').update(String(key || '') + GUID).digest('base64'); }
function frame(opcode, payload) {
  const body = Buffer.isBuffer(payload) ? payload : Buffer.from(String(payload || ''), 'utf8');
  const header = [0x80 | (opcode & 0x0f)];
  if (body.length <= 125) header.push(body.length);
  else header.push(126, (body.length >> 8) & 0xff, body.length & 0xff);
  return Buffer.concat([Buffer.from(header), body]);
}
function readMasked(state, chunk) {
  state.buffer = state.buffer.length ? Buffer.concat([state.buffer, chunk]) : chunk;
  const out = [];
  let offset = 0;
  while (state.buffer.length - offset >= 2) {
    const first = state.buffer[offset];
    const second = state.buffer[offset + 1];
    const opcode = first & 0x0f;
    const masked = (second & 0x80) !== 0;
    let length = second & 0x7f;
    let cursor = offset + 2;
    if (length === 126) { if (state.buffer.length - cursor < 2) break; length = state.buffer.readUInt16BE(cursor); cursor += 2; }
    if (!masked || state.buffer.length - cursor < 4 + length) break;
    const mask = state.buffer.subarray(cursor, cursor + 4); cursor += 4;
    const payload = Buffer.from(state.buffer.subarray(cursor, cursor + length)); cursor += length;
    for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
    out.push({ opcode, payload });
    offset = cursor;
  }
  state.buffer = state.buffer.subarray(offset);
  return out;
}
const server = http.createServer();
server.on('upgrade', (req, socket) => {
  socket.write(['HTTP/1.1 101 Switching Protocols','Upgrade: websocket','Connection: Upgrade',`Sec-WebSocket-Accept: ${accept(req.headers['sec-websocket-key'])}`,'',''].join('\r\n'));
  const state = { buffer: Buffer.alloc(0), replied: false };
  socket.on('data', chunk => {
    for (const item of readMasked(state, chunk)) {
      if (item.opcode !== 1) continue;
      let event = {};
      try { event = JSON.parse(item.payload.toString('utf8')); } catch {}
      if (event.type === 'session.update') {
        socket.write(frame(1, JSON.stringify({ type: 'session.updated', session: { model: 'fake-qwen-realtime' } })));
      }
      if (event.type === 'input_audio_buffer.append' && !state.replied) {
        state.replied = true;
        socket.write(frame(1, JSON.stringify({ type: 'conversation.item.input_audio_transcription.completed', transcript: 'realtime audio transcription e2e' })));
        socket.write(frame(1, JSON.stringify({ type: 'response.audio_transcript.delta', delta: 'I heard you.' })));
        socket.write(frame(1, JSON.stringify({ type: 'response.audio.delta', delta: Buffer.from(new Int16Array([0, 1200, 0, -1200, 0, 900, 0, -900]).buffer).toString('base64') })));
        socket.write(frame(1, JSON.stringify({ type: 'response.done', response: { output: [{ content: [{ transcript: 'I heard you. This is a care reminder, not a diagnosis.' }] }] } })));
      }
      if (event.type === 'response.cancel') {
        console.log('response.cancel');
      }
      if (event.type === 'input_audio_buffer.clear') {
        console.log('input_audio_buffer.clear');
      }
    }
  });
});
server.listen(port, '127.0.0.1');
'@
$serverCommand = @"
`$env:GOUXIONG_PORT='$port'
`$env:GOUXIONG_HOST='0.0.0.0'
`$env:GOUXIONG_DEV_SMS='1'
`$env:GOUXIONG_DATA_DIR='$dataDir'
`$env:GOUXIONG_DB_PATH='$dbPath'
`$env:ALIYUN_REALTIME_ENABLED='1'
`$env:ALIYUN_REALTIME_API_KEY='voice-e2e-realtime-key'
`$env:ALIYUN_REALTIME_MODEL='fake-qwen-realtime'
`$env:ALIYUN_REALTIME_ENDPOINT='ws://127.0.0.1:$realtimePort/realtime'
[Environment]::SetEnvironmentVariable('GOUXIONG_SERVER_SECRET', 'voice-e2e-server-secret-$port-0123456789abcdef', 'Process')
[Environment]::SetEnvironmentVariable('GOUXIONG_ADMIN_TOKEN', 'voice-e2e-admin-token-$port-0123456789abcdef', 'Process')
node '$serverScript'
"@

$serverProcess = $null
$fakeRealtimeProcess = $null
try {
  Log "== Start fake Aliyun Realtime server on port $realtimePort =="
  $fakeRealtimeProcess = Start-Process -FilePath node -ArgumentList @($fakeRealtimeScript, "$realtimePort") -PassThru -WindowStyle Hidden -RedirectStandardOutput $fakeRealtimeLog -RedirectStandardError $fakeRealtimeErr
  WaitPort $realtimePort

  Log "== Start temporary voice E2E server on port $port =="
  $serverProcess = Start-Process -FilePath powershell -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $serverCommand) -PassThru -WindowStyle Hidden -RedirectStandardOutput $serverLog -RedirectStandardError $serverErr
  $baseLocal = "http://127.0.0.1:$port"
  $baseEmulator = "http://10.0.2.2:$port"
  $health = WaitHealth $baseLocal
  if ($health.sms.provider -ne "dev") { throw "E2E server must use dev SMS." }

  Log "== Create temporary user and profile =="
  $phone = "1380023$port"
  $sent = PostJson $baseLocal "/api/auth/request-code" @{ phone = $phone }
  $verified = PostJson $baseLocal "/api/auth/verify" @{ phone = $phone; code = $sent.dev_code; device_id = "apk-voice-e2e-$port" }
  $token = $verified.token
  if (-not $token) { throw "E2E token missing." }
  [void](Invoke-RestMethod -Method Put -Uri ($baseLocal + "/api/profile") -ContentType "application/json; charset=utf-8" -Headers @{ Authorization = "Bearer $token" } -Body (JsonBody @{
    assistant_name = "Nuannuan"
    assistant_identity = "caring family helper"
    owner_address = "Nainai"
    health = "high blood pressure, occasional dizziness"
    medication = "morning blood-pressure medicine as prescribed"
    sleep = "wakes at night, occasional snoring"
    family = "daughter lives nearby"
    hobbies = "walking and opera"
    care_preference = "short, warm, direct voice reminders"
  }) -TimeoutSec 30)

  $device = FirstDevice
  Log "== Install APK and inject server auth on $device =="
  InjectPrefs $device $baseEmulator $phone $token ([int]$verified.user_id)

  Log "== Trigger simulated recognized speech =="
  $utterance = "I_woke_up_twice_last_night_and_feel_dizzy_today_please_comfort_me_safely"
  TriggerDebugVoiceText $device $utterance
  Start-Sleep -Seconds 2

  Log "== Wait for Live stream and model audio =="
  $ttsDeltas = WaitLiveTtsDeltas $device 30
  if ($ttsDeltas -lt 1) {
    throw "APK did not record consumed sentence_delta events from /api/live/session."
  }
  $audioFrames = WaitLiveAudioFrames $device 20
  if ($audioFrames -lt 1) {
    throw "APK did not stream live microphone audio frames to /api/live/session."
  }
  $modelAudioFrames = WaitLiveModelAudioFrames $device 20
  if ($modelAudioFrames -lt 1) {
    throw "APK did not play model audio frames returned through /api/live/session."
  }
  $emotionTags = WaitLiveEmotionTag $device 20 "model_reply_analysis"
  if ($emotionTags -lt 1) {
    throw "APK did not record structured model emotion tags from /api/live/session."
  }

  Log "== Trigger Live auto barge-in interruption =="
  TriggerLiveAutoBargeIn $device
  if ($LASTEXITCODE -ne 0) { throw "live auto barge-in intent failed" }
  if (-not (WaitLiveAutoBargeIn $device 10)) {
    throw "APK did not record automatic Live barge-in state."
  }
  if (-not (WaitLiveVoiceInterrupted $device 20)) {
    throw "APK did not record the Live interrupt response from /api/live/session."
  }
  if (-not (WaitLogContains $fakeRealtimeLog "response.cancel" 20) -or -not (WaitLogContains $fakeRealtimeLog "input_audio_buffer.clear" 20)) {
    throw "Fake Aliyun Realtime did not receive response.cancel and input_audio_buffer.clear."
  }

  Log "== Verify server chat persistence =="
  $exported = WaitServerChatPersistence $baseLocal $token 90
  $allChat = ($exported.chat_messages | ConvertTo-Json -Depth 6)
  if ($allChat -notmatch "I_woke_up_twice" -or $exported.chat_messages.Count -lt 1) {
    throw "Server chat messages did not include the simulated user voice turn."
  }
  $allInsights = ($exported.insights | ConvertTo-Json -Depth 6)
  if ($exported.insights.Count -gt 0 -and $allInsights -notmatch "live_voice") {
    throw "Unexpected insight source for the simulated voice conversation."
  }

  Log "== Verify voice shortcuts route to real companion capabilities =="
  AssertVoiceShortcutRoute $device "play_rain_sound" "music_playback" 20
  if (-not (WaitLiveVoiceReply $device 20 "本地助眠音" "本地生成")) {
    throw "Voice shortcut did not start local sleep sound."
  }
  AssertVoiceShortcutRoute $device "stop_rain_sound" "music_playback_stop" 20
  if (-not (WaitLiveVoiceReply $device 20 "助眠音已淡出" "离线合成助眠音")) {
    throw "Voice shortcut did not stop local sleep sound."
  }
  AssertVoiceShortcutRoute $device "read_news" "news_briefing" 20
  if (-not (WaitLiveVoiceReply $device 20 "新闻源还没接入" "不能凭空编今天的新闻")) {
    throw "Voice shortcut did not check real news source status."
  }
  AssertVoiceShortcutRoute $device "tell_bedtime_story" "bedtime_story" 20

  Log "== Verify possible-asleep confirmation continues when user is awake =="
  TriggerDebugSleepCheck $device
  if (-not (WaitLiveVoiceStage $device 15 "sleep_check" "可能睡着确认" "您睡了么")) {
    throw "APK did not enter possible-asleep confirmation state."
  }
  TriggerDebugVoiceText $device "not_asleep_continue"
  if (-not (WaitLiveVoiceReply $device 20 "我继续陪您" "好的，那我继续")) {
    throw "APK did not continue companion playback after not-asleep reply."
  }
  $afterAwakeStage = LatestLiveVoiceStage $device
  if ($afterAwakeStage -eq "sleep_guard") {
    throw "APK entered sleep guard even though user replied not asleep."
  }

  Log "== Verify possible-asleep confirmation enters guard when user is ready =="
  TriggerDebugSleepCheck $device
  if (-not (WaitLiveVoiceStage $device 15 "sleep_check" "可能睡着确认" "您睡了么")) {
    throw "APK did not re-enter possible-asleep confirmation state."
  }
  TriggerDebugVoiceText $device "asleep_now"
  if (-not (WaitLiveVoiceStage $device 20 "sleep_guard" "转入睡眠守护" "安心睡")) {
    throw "APK did not enter sleep guard after asleep reply."
  }

  Log "== Verify possible-asleep confirmation enters guard after no reply =="
  TriggerDebugSleepCheckTimeout $device
  if (-not (WaitLiveVoiceStage $device 20 "sleep_guard" "转入睡眠守护" "没听到您回答")) {
    throw "APK did not enter sleep guard after no-reply timeout."
  }

  if (-not $KeepInjectedPrefs) {
    Log "== Reset emulator prefs to local 8787 =="
    ResetPrefsToLocalServer $device
  }

  Log "APK live voice E2E passed."
  [pscustomobject]@{
    ok = $true
    device = $device
    server_port = $port
    sms_provider = $health.sms.provider
    model_provider = $health.model.provider
    chat_messages = $exported.chat_messages.Count
    live_tts_deltas = $ttsDeltas
    live_emotion_tags = $emotionTags
    live_audio_frames = $audioFrames
    live_model_audio_frames = $modelAudioFrames
    live_interrupt = $true
    live_auto_barge_in = $true
    voice_shortcuts = "story/news/local-sleep-sound"
    sleep_check_awake_reply = $true
    sleep_check_asleep_reply = $true
    sleep_check_no_reply = $true
    apk_flow = "DEBUG_VOICE_TEXT -> /api/live/session WebSocket -> Live reply -> automatic PCM barge-in interrupt"
  } | ConvertTo-Json -Compress
} catch {
  Log "APK live voice E2E failed: $($_.Exception.Message)"
  if (Test-Path $serverLog) {
    Add-Content -Path $runLog -Value "--- server stdout tail ---" -Encoding UTF8
    Get-Content -Path $serverLog -Tail 40 -ErrorAction SilentlyContinue | Add-Content -Path $runLog -Encoding UTF8
  }
  if (Test-Path $serverErr) {
    Add-Content -Path $runLog -Value "--- server stderr tail ---" -Encoding UTF8
    Get-Content -Path $serverErr -Tail 40 -ErrorAction SilentlyContinue | Add-Content -Path $runLog -Encoding UTF8
  }
  throw
} finally {
  if ($serverProcess -and -not $serverProcess.HasExited) {
    Stop-Process -Id $serverProcess.Id -Force
  }
  if ($fakeRealtimeProcess -and -not $fakeRealtimeProcess.HasExited) {
    Stop-Process -Id $fakeRealtimeProcess.Id -Force
  }
  StopE2EPort $port
  StopE2EPort $realtimePort
}
