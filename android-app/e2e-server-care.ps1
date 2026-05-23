param(
  [switch]$KeepInjectedPrefs
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $root
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { throw "ANDROID_HOME or ANDROID_SDK_ROOT is not set." }

$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb not found: $adb" }

$apk = Join-Path $root "build\outputs\apk\gouxiong-sleep-debug.apk"
if (-not (Test-Path $apk)) {
  throw "APK missing. Run android-app\test.ps1 first: $apk"
}

function JsonBody($value) {
  return ($value | ConvertTo-Json -Depth 8 -Compress)
}

function PostJson($base, $path, $body, $token = "") {
  $headers = @{}
  if ($token) { $headers.Authorization = "Bearer $token" }
  return Invoke-RestMethod -Method Post -Uri ($base + $path) -ContentType "application/json; charset=utf-8" -Headers $headers -Body (JsonBody $body) -TimeoutSec 45
}

function GetJson($base, $path, $token = "") {
  $headers = @{}
  if ($token) { $headers.Authorization = "Bearer $token" }
  return Invoke-RestMethod -Method Get -Uri ($base + $path) -Headers $headers -TimeoutSec 20
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

function WaitTopActivity($device, $activityName, $seconds = 20) {
  for ($i = 0; $i -lt $seconds; $i++) {
    $raw = & $adb -s $device shell dumpsys activity activities
    $joined = ($raw -join "`n")
    if ($joined -match [regex]::Escape($activityName)) {
      return $true
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

function EscapeXml($value) {
  return [System.Security.SecurityElement]::Escape([string]$value)
}

function InstallAndInjectPrefs($device, $serverBaseForEmulator, $phone, $token, $userId) {
  & $adb -s $device install -r $apk | Write-Host
  if ($LASTEXITCODE -ne 0) { throw "adb install failed" }

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
    <boolean name="hydration_reminder_enabled" value="true" />
    <boolean name="medication_enabled" value="true" />
    <string name="medication_name">morning blood-pressure medicine</string>
    <boolean name="monitoring" value="false" />
</map>
"@
  $tmpXml = Join-Path $env:TEMP "gouxiong_sleep_prefs_e2e.xml"
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($tmpXml, $xml, $utf8NoBom)

  & $adb -s $device push $tmpXml /data/local/tmp/gouxiong_sleep_prefs_e2e.xml | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "adb push prefs failed" }
  & $adb -s $device shell chmod 644 /data/local/tmp/gouxiong_sleep_prefs_e2e.xml | Out-Null
  & $adb -s $device shell run-as com.gouxiong.sleep sh -c "'mkdir -p shared_prefs && cp /data/local/tmp/gouxiong_sleep_prefs_e2e.xml shared_prefs/gouxiong_sleep_prefs.xml && chmod 600 shared_prefs/gouxiong_sleep_prefs.xml'" | Out-Null
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
  $tmpXml = Join-Path $env:TEMP "gouxiong_sleep_prefs_reset.xml"
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($tmpXml, $xml, $utf8NoBom)
  & $adb -s $device shell am force-stop com.gouxiong.sleep | Out-Null
  & $adb -s $device push $tmpXml /data/local/tmp/gouxiong_sleep_prefs_reset.xml | Out-Null
  & $adb -s $device shell chmod 644 /data/local/tmp/gouxiong_sleep_prefs_reset.xml | Out-Null
  & $adb -s $device shell run-as com.gouxiong.sleep sh -c "'mkdir -p shared_prefs && cp /data/local/tmp/gouxiong_sleep_prefs_reset.xml shared_prefs/gouxiong_sleep_prefs.xml && chmod 600 shared_prefs/gouxiong_sleep_prefs.xml'" | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "run-as prefs reset failed" }
}

function DumpUi($device) {
  $remote = "/sdcard/gouxiong-window.xml"
  $localDump = Join-Path $env:TEMP "gouxiong-window.xml"
  if (Test-Path $localDump) { Remove-Item -LiteralPath $localDump -Force }
  & $adb -s $device shell rm -f $remote | Out-Null
  & $adb -s $device shell uiautomator dump --compressed $remote | Out-Null
  if ($LASTEXITCODE -ne 0) { return "" }
  & $adb -s $device pull $remote $localDump | Out-Null
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path $localDump)) { return "" }
  return Get-Content -Encoding UTF8 $localDump -Raw
}

function TapTextInXml($device, $raw, $text) {
  $escaped = [regex]::Escape($text)
  $pattern = "text=`"$escaped`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`""
  if ($raw -notmatch $pattern) {
    return $false
  }
  $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
  $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
  & $adb -s $device shell input tap $x $y | Out-Null
  return $true
}

function TapText($device, $text) {
  for ($i = 0; $i -lt 6; $i++) {
    $raw = DumpUi $device
    if (TapTextInXml $device $raw $text) {
      return
    }
    if ($raw -match "WeChat isn't responding" -and (TapTextInXml $device $raw "Close app")) {
      Start-Sleep -Seconds 1
      continue
    }
    if ($raw -match "isn't responding" -and (TapTextInXml $device $raw "Wait")) {
      Start-Sleep -Seconds 1
      continue
    }
    Start-Sleep -Seconds 1
  }
  throw "Could not find tappable text '$text' in UI dump."
}

function WaitMessageRead($base, $token, $messageId) {
  for ($i = 0; $i -lt 20; $i++) {
    $pending = GetJson $base "/api/messages/pending" $token
    $stillThere = $false
    foreach ($msg in $pending.messages) {
      if ([int]$msg.id -eq [int]$messageId) { $stillThere = $true }
    }
    if (-not $stillThere) { return $true }
    Start-Sleep -Seconds 1
  }
  return $false
}

$portCandidates = 8792..8802
$port = $null
foreach ($candidate in $portCandidates) {
  $busy = netstat -ano | Select-String ":$candidate" | Select-String "LISTENING"
  if (-not $busy) { $port = $candidate; break }
}
if (-not $port) { throw "No free E2E port found in $($portCandidates -join ', ')" }

$dataLeaf = "gouxiong-sleep-e2e-data-$port"
$dataDir = Join-Path $env:TEMP $dataLeaf
$dbPath = Join-Path $dataDir "e2e.sqlite3"
ResetTempDataDir $dataDir $dataLeaf
$runLog = Join-Path $env:TEMP "gouxiong-sleep-e2e-$port.run.log"
Set-Content -Path $runLog -Value "APK server care E2E run started at $(Get-Date -Format o)" -Encoding UTF8

function Log($message) {
  $line = "$(Get-Date -Format HH:mm:ss) $message"
  Add-Content -Path $script:runLog -Value $line -Encoding UTF8
  Write-Output $message
}

$serverScript = Join-Path $projectRoot "server\gouxiong-server.mjs"
$serverLog = Join-Path $env:TEMP "gouxiong-sleep-e2e-$port.log"
$serverErr = Join-Path $env:TEMP "gouxiong-sleep-e2e-$port.err.log"
$serverCommand = @"
`$env:GOUXIONG_PORT='$port'
`$env:GOUXIONG_HOST='0.0.0.0'
`$env:GOUXIONG_DEV_SMS='1'
`$env:GOUXIONG_DATA_DIR='$dataDir'
`$env:GOUXIONG_DB_PATH='$dbPath'
`$env:GOUXIONG_DISABLE_MODEL='1'
`$env:DASHSCOPE_API_KEY=''
`$env:ALIYUN_MODEL_API_KEY=''
`$env:ALIYUN_BAILIAN_API_KEY=''
`$env:DEEPSEEK_API_KEY=''
`$env:ALIYUN_REALTIME_ENABLED=''
`$env:ALIYUN_REALTIME_API_KEY=''
[Environment]::SetEnvironmentVariable('GOUXIONG_SERVER_SECRET', 'e2e-server-secret-$port-0123456789abcdef', 'Process')
[Environment]::SetEnvironmentVariable('GOUXIONG_ADMIN_TOKEN', 'e2e-admin-token-$port-0123456789abcdef', 'Process')
node '$serverScript'
"@

$serverProcess = $null
try {
  Log "== Start temporary E2E server on port $port =="
  $serverProcess = Start-Process -FilePath powershell -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $serverCommand) -PassThru -WindowStyle Hidden -RedirectStandardOutput $serverLog -RedirectStandardError $serverErr
  $baseLocal = "http://127.0.0.1:$port"
  $baseEmulator = "http://10.0.2.2:$port"
  $health = WaitHealth $baseLocal
  if ($health.sms.provider -ne "dev") { throw "E2E server must use dev SMS." }

  Log "== Create temporary user with dev SMS =="
  $phone = "1380013$port"
  $sent = PostJson $baseLocal "/api/auth/request-code" @{ phone = $phone }
  if (-not $sent.dev_code) { throw "E2E dev code missing." }
  $verified = PostJson $baseLocal "/api/auth/verify" @{ phone = $phone; code = $sent.dev_code; device_id = "apk-e2e-$port" }
  $token = $verified.token
  if (-not $token) { throw "E2E token missing." }

  Log "== Sync profile and queue care message =="
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

  $care = PostJson $baseLocal "/api/care/brief" @{
    type = "morning"
    context = @{
      owner_address = "Nainai"
      assistant_role = "caring family helper"
      sleep_summary = "woke twice last night, possible snoring once"
      owner_profile = "high blood pressure, morning medicine as prescribed"
    }
    create_message = $true
  } $token
  if (-not $care.body -or [int]$care.message_id -le 0) { throw "care brief did not create a queued message." }

  $device = FirstDevice
  Log "== Install APK and inject server auth on $device =="
  InstallAndInjectPrefs $device $baseEmulator $phone $token ([int]$verified.user_id)

  Log "== Trigger APK server-message poll =="
  & $adb -s $device shell input keyevent 82 | Out-Null
  & $adb -s $device shell am start -W -a com.gouxiong.sleep.action.DEBUG_SERVER_MESSAGE_POLL -n com.gouxiong.sleep/.MainActivity | Out-Null
  if (-not (WaitTopActivity $device "com.gouxiong.sleep/.ProactiveCareActivity" 25)) {
    throw "APK did not open ProactiveCareActivity after server-message poll."
  }

  Log "== Confirm voice message and verify read-back =="
  $heardText = -join ([char[]](0x6211, 0x542C, 0x5230, 0x4E86))
  TapText $device $heardText
  if (-not (WaitMessageRead $baseLocal $token ([int]$care.message_id))) {
    throw "Queued care message was not marked read by APK."
  }

  $me = GetJson $baseLocal "/api/me" $token
  if (-not $me.profile -or $me.user.id -ne $verified.user_id) {
    throw "APK E2E user/profile verification failed."
  }

  if (-not $KeepInjectedPrefs) {
    Log "== Reset emulator prefs to local 8787 =="
    ResetPrefsToLocalServer $device
  }

  Log "APK server care E2E passed."
  [pscustomobject]@{
    ok = $true
    device = $device
    server_port = $port
    sms_provider = $health.sms.provider
    model_provider = $health.model.provider
    message_id = [int]$care.message_id
    apk_activity = "ProactiveCareActivity"
  } | ConvertTo-Json -Compress
} catch {
  Log "APK server care E2E failed: $($_.Exception.Message)"
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
  StopE2EPort $port
}
