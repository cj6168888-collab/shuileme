$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk) { throw "ANDROID_HOME or ANDROID_SDK_ROOT is not set." }

Write-Host "== Build APK =="
& powershell -ExecutionPolicy Bypass -File (Join-Path $root "build.ps1")
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

$apk = Join-Path $root "build\outputs\apk\gouxiong-sleep-debug.apk"
if (-not (Test-Path $apk)) { throw "APK missing: $apk" }

$buildToolsRoot = Join-Path $sdk "build-tools"
$bt = Get-ChildItem -Path $buildToolsRoot -Directory -ErrorAction SilentlyContinue |
  Where-Object { (Test-Path (Join-Path $_.FullName "aapt2.exe")) -or (Test-Path (Join-Path $_.FullName "aapt2")) } |
  Sort-Object Name -Descending |
  Select-Object -First 1 |
  ForEach-Object { $_.FullName }
if (-not $bt) { throw "No Android build-tools found under $buildToolsRoot." }
$apksigner = Join-Path $bt "apksigner.bat"
$aapt2 = Join-Path $bt "aapt2.exe"
if (-not (Test-Path $apksigner)) { $apksigner = Join-Path $bt "apksigner" }
if (-not (Test-Path $aapt2)) { $aapt2 = Join-Path $bt "aapt2" }

Write-Host "== Verify signature =="
& $apksigner verify --verbose $apk
if ($LASTEXITCODE -ne 0) { throw "Signature verify failed" }

Write-Host "== Package name =="
$packageName = & $aapt2 dump packagename $apk
if ($packageName.Trim() -ne "com.gouxiong.sleep") { throw "Unexpected package: $packageName" }
Write-Host $packageName

Write-Host "== Permissions =="
$permissions = & $aapt2 dump permissions $apk
$required = @(
  "android.permission.RECORD_AUDIO",
  "android.permission.POST_NOTIFICATIONS",
  "android.permission.FOREGROUND_SERVICE",
  "android.permission.FOREGROUND_SERVICE_MICROPHONE",
  "android.permission.USE_FULL_SCREEN_INTENT",
  "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
  "android.permission.WAKE_LOCK",
  "android.permission.VIBRATE",
  "android.permission.CALL_PHONE",
  "android.permission.SEND_SMS",
  "android.permission.SCHEDULE_EXACT_ALARM",
  "android.permission.INTERNET"
)
foreach ($perm in $required) {
  if (($permissions -join "`n") -notmatch [regex]::Escape($perm)) {
    throw "Missing permission: $perm"
  }
}
$permissions | Write-Host

Write-Host "== Static checks =="
$service = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\SleepMonitorService.java") -Raw
foreach ($needle in @("sleep_guard_silent_v2", "setSound(null, null)", "enableVibration(false)", "IMPORTANCE_LOW", "sleep_alarm_v2", "IMPORTANCE_HIGH", "morning_care_quiet_v1", "maybePromptMorningCare", "showMorningCarePrompt", "markMorningPromptedToday", "loadSignalBaseline", "updateSignalBaselineIfCalm", "mediumRmsThreshold", "baselineEvidenceText")) {
  if ($service -notmatch [regex]::Escape($needle)) { throw "Static check failed: $needle" }
}
$main = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\MainActivity.java") -Raw
foreach ($needle in @("showPreSleepCheck", "heartbeatText", "testGentleReminder", "preSleepStatusText", "requestIgnoreBatteryOptimization", "batteryOptimizationText", "guardIntegrityScore", "detectionConfidenceText", "showDetectionTest", "simulateEvent", "showMorningCare", "showMedicationDialog", "scheduleMedicationReminder", "showCompanionSettings", "addCompanionChoice", "showOwnerProfileSettings", "showOwnerProfileDialog", "proactiveCareText", "maybeShowProactiveCare", "startAssistantMotion", "showDeepSeekSettings", "showDeepSeekQuestionDialog", "askDeepSeek", "deepSeekSystemPrompt", "deepSeekUserPrompt", "showCompanionChat", "showCompanionReply", "addAssistantHero", "speakAssistantText", "assistantOnlineEnabled", "ownerProfileSummary", "assistantProactiveCareEnabled", "onNewIntent", "shouldOpenMorningCare", "showEvidenceSettings", "showDeviceReadingDialog", "deviceReadingsText", "createDemoEvidenceClip", "showEventReviewDialog", "playEvidenceAudio", "signalBaselineSamples", "signalAudioBaselineRms", "signalMotionBaseline", "safeTopPadding", "statusBarHeight", "setEmergencyContacts", "MAX_EMERGENCY_CONTACTS", "emergencyActionSummary", "keySaved")) {
  if ($main -notmatch [regex]::Escape($needle)) { throw "Static check failed: $needle" }
}
$alarm = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\AlarmActivity.java") -Raw
foreach ($needle in @("CompanionAssistant", "wakeLine", "confirmLine", "assistantRole")) {
  if ($alarm -notmatch [regex]::Escape($needle)) { throw "Alarm static check failed: $needle" }
}
$companion = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\util\CompanionAssistant.java") -Raw
foreach ($needle in @("ROLE_SISTER", "ROLE_BROTHER", "ROLE_YOUNG_MAN", "ROLE_GENTLE_WOMAN", "chatSleepReport", "chatPrivacy", "sampleLine")) {
  if ($companion -notmatch [regex]::Escape($needle)) { throw "Companion static check failed: $needle" }
}
$deepSeek = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\util\DeepSeekClient.java") -Raw
foreach ($needle in @("https://api.deepseek.com/chat/completions", "Authorization", "deepseek-v4-flash", "thinking")) {
  if ($deepSeek -notmatch [regex]::Escape($needle)) { throw "DeepSeek static check failed: $needle" }
}
$prefs = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\util\PreferenceStore.java") -Raw
foreach ($needle in @("MAX_EMERGENCY_CONTACTS", "emergencyPhone(int index)", "emergencyPhones", "setEmergencyContacts", "emergencySummary", "emergencyActionSummary", "AndroidKeyStore", "deepseek_api_key_encrypted_v1", "encryptSecret", "decryptSecret", "GCMParameterSpec", "remove(LEGACY_DEEPSEEK_API_KEY)")) {
  if ($prefs -notmatch [regex]::Escape($needle)) { throw "Preference static check failed: $needle" }
}
$notifier = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\util\EmergencyNotifier.java") -Raw
foreach ($needle in @("String[] phones", "sendSms(activity, phones", "sendTextMessage", "call(activity, phones[0])")) {
  if ($notifier -notmatch [regex]::Escape($needle)) { throw "Emergency notifier static check failed: $needle" }
}
$db = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\data\SleepDatabase.java") -Raw
foreach ($needle in @("audio_path", "audio_summary", "motion_summary", "device_summary", "evidence_level", "doctorReportText", "device_readings", "insertDeviceReading", "nearestDeviceEvidence", "findNearestDeviceReading")) {
  if ($db -notmatch [regex]::Escape($needle)) { throw "Database static check failed: $needle" }
}
$deviceReading = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\model\DeviceReading.java") -Raw
foreach ($needle in @("DeviceReading", "heartRate", "spo2", "respiratoryRate", "fromCursor")) {
  if ($deviceReading -notmatch [regex]::Escape($needle)) { throw "Device reading static check failed: $needle" }
}
$wav = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\util\WavFileWriter.java") -Raw
foreach ($needle in @("writePcm16Mono", "RIFF", "WAVE", "data")) {
  if ($wav -notmatch [regex]::Escape($needle)) { throw "WAV static check failed: $needle" }
}
$manifest = Get-Content -Encoding UTF8 (Join-Path $root "src\main\AndroidManifest.xml") -Raw
foreach ($needle in @("MedicationReminderReceiver", "SCHEDULE_EXACT_ALARM", "android:debuggable=`"true`"")) {
  if ($manifest -notmatch [regex]::Escape($needle)) { throw "Manifest check failed: $needle" }
}

Write-Host "== Local secret leak check =="
$localConfig = Join-Path $root "local.deepseek.properties"
if (Test-Path $localConfig) {
  $secret = $null
  foreach ($line in Get-Content -Encoding UTF8 $localConfig) {
    if ($line -match '^deepseek\.apiKey=(.+)$') { $secret = $Matches[1].Trim() }
  }
  if ($secret -and $secret.Length -gt 8) {
    $apkText = [System.Text.Encoding]::ASCII.GetString([System.IO.File]::ReadAllBytes($apk))
    if ($apkText.Contains($secret)) {
      throw "Local DeepSeek secret leaked into APK"
    }
    Write-Host "Local DeepSeek key is not present in APK."
  } else {
    Write-Host "Local config exists, but no valid key was found for leak check."
  }
} else {
  Write-Host "No local DeepSeek config found; skipping local key leak check."
}

Write-Host "== Device check =="
& (Join-Path $sdk "platform-tools\adb.exe") devices -l
Write-Host "If no device is listed above, local build tests are complete but true device interaction is not covered."

Write-Host "== APK hash =="
Get-FileHash $apk -Algorithm SHA256
