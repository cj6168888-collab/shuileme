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
  "android.permission.CAMERA",
  "android.permission.POST_NOTIFICATIONS",
  "android.permission.FOREGROUND_SERVICE",
  "android.permission.FOREGROUND_SERVICE_MICROPHONE",
  "android.permission.USE_FULL_SCREEN_INTENT",
  "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
  "android.permission.WAKE_LOCK",
  "android.permission.VIBRATE",
  "android.permission.BLUETOOTH_CONNECT",
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
foreach ($needle in @("sleep_guard_silent_v2", "setSound(null, null)", "enableVibration(false)", "IMPORTANCE_LOW", "sleep_alarm_v2", "IMPORTANCE_HIGH", "morning_care_quiet_v1", "maybePromptMorningCare", "showMorningCarePrompt", "markMorningPromptedToday", "loadSignalBaseline", "updateSignalBaselineIfCalm", "mediumRmsThreshold", "baselineEvidenceText", "insertSignalSample", "signalState")) {
  if ($service -notmatch [regex]::Escape($needle)) { throw "Static check failed: $needle" }
}
$main = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\MainActivity.java") -Raw
$devDrillLabel = -join ([char[]](0x5f00, 0x53d1, 0x8005, 0x6f14, 0x7ec3, 0x6d4b, 0x8bd5))
$realWaveLabel = -join ([char[]](0x771f, 0x5b9e, 0x6ce2, 0x5f62))
$realSmsHint = (-join ([char[]](0x4e0d, 0x4f1a, 0x5728))) + " App " + (-join ([char[]](0x91cc, 0x663e, 0x793a, 0x9a8c, 0x8bc1, 0x7801)))
foreach ($needle in @("showPreSleepCheck", "heartbeatText", "testGentleReminder", "preSleepStatusText", "requestIgnoreBatteryOptimization", "batteryOptimizationText", "guardIntegrityScore", "detectionConfidenceText", "showDetectionTest", "simulateEvent", "showMorningCare", "morningBriefText", "showWellnessTip", "wellness_tip", "showMedicationDialog", "scheduleMedicationReminder", "showCompanionSettings", "addCompanionChoice", "showOwnerProfileSettings", "showOwnerProfileDialog", "showOwnerProfileWizard", "saveOwnerProfileWizardStep", "saveOwnerProfileField", "showAssistantPersonaDialog", "saveAssistantPersonaFromMessage", "extractAssistantName", "extractOwnerAddress", "firstMeetingPromptedThisSession", "proactiveCareText", "maybeShowProactiveCare", "startAssistantMotion", "showServerAccountSettings", "exportServerAccountData", "confirmDeleteServerAccount", "deleteServerAccountData", "showServerCapabilityCheck", "showServerCapabilityResult", "showPhoneLoginDialog", "requestServerCode", "verifyServerCode", "syncOwnerProfileQuiet", "fetchServerMessagesForReadingSilently", "openServerMessagesFromIntent", "shouldOpenServerMessages", "ACTION_OPEN_SERVER_MESSAGES", "ACTION_OPEN_COMPANION", "ACTION_DEBUG_HYDRATION_CARE", "ACTION_DEBUG_SERVER_MESSAGE_CARE", "ACTION_DEBUG_SERVER_MESSAGE_POLL", "ACTION_DEBUG_NIGHTMARE_WAKE", "ACTION_DEBUG_VOICE_TEXT", "ACTION_DEBUG_SLEEP_CHECK", "ACTION_DEBUG_SLEEP_CHECK_TIMEOUT", "ACTION_DEBUG_LIVE_ABORT", "ACTION_DEBUG_LIVE_BARGE_IN", "ACTION_DEBUG_CAMERA_GLANCE", "debug_voice_text", "handleDebugCareIntent", "openCompanionFromIntent", "showDeepSeekQuestionDialog", "askDeepSeek", "looksLikeFindObjectQuestion", "addAiQuestionButton", "deepSeekSystemPrompt", "deepSeekUserPrompt", "showCompanionChat", "addAssistantChatModeSwitch", "toggleAssistantChatMode", "MotionEvent.ACTION_UP", "assistantVideoChatMode", "debugCompanionUiTestMode", "setCharacterResource", "setCharacterBitmapMode", "roleAvatarResourceId", "文字聊天", "视频聊天", "addRealtimeVoicePanel", "startRealtimeVoiceChat", "ensureLiveCompanionSession", "liveCompanionEndpoint", "/api/live/session", "trySendLiveCompanionText", "flushPendingLiveCompanionText", "startLiveAudioStreamingIfPossible", "sendLiveAudioFrame", "stopLiveAudioStreaming", "playLiveModelAudioFrame", "stopLiveModelAudioPlayback", "maybeAutoBargeInFromPcm", "updateLiveBargeInNoiseFloor", "liveBargeInSpeechThresholdRms", "liveBargeInExitThresholdRms", "debugFeedAutoBargeInFrames", "liveOutputActiveForBargeIn", "triggerAutoLiveBargeIn", "LIVE_BARGE_IN_RMS_THRESHOLD", "LIVE_BARGE_IN_MIN_SPEECH_MS", "LIVE_BARGE_IN_NOISE_MULTIPLIER", "LIVE_BARGE_IN_CONSECUTIVE_FRAMES", "LivePcmPlayer", "startHttpVoiceAnswer", "abortLiveCompanionSpeech", "SpeechRecognizer", "RecognizerIntent", "handleRealtimeVoiceText", "handleLiveTtsDelta", "finishLiveTtsReply", "sentence_delta", "speakLiveStreamingChunk", "speakAssistantTextQueued", "isLiveStreamingInterimUtterance", "liveStageSpeechLabel", "showCompanionVoiceReply", "stopAssistantSpeech", "showCompanionReply", "showCompanionWaiting", "showCompanionVision", "requestCameraForVision", "handleVisionSnapshot", "askDeepSeekVision", "maybeStartAutoVisionScan", "startAutoVisionCapture", "deepSeekAutoVisionPrompt", "storeAutoVisionMemory", "recordVisionCaptureState", "bitmapToLimitedJpeg", "VISION_MAX_JPEG_BYTES", "CameraDevice.TEMPLATE_STILL_CAPTURE", "bitmapToJpegBase64", "decodeVisionBitmap", "MediaStore.EXTRA_OUTPUT", "showObjectMemoryDialog", "showFindObjectDialog", "addAssistantHero", "roleAvatarAssetName", "speakAssistantText", "ownerProfileSummary", "assistantProactiveCareEnabled", "assistantAutoVisionEnabled", "assistantPersonaSummary", "onNewIntent", "shouldOpenMorningCare", "showEvidenceSettings", "showDeviceReadingDialog", "deviceReadingsText", "createDemoEvidenceClip", "showEventReviewDialog", "playEvidenceAudio", "signalBaselineSamples", "signalAudioBaselineRms", "signalMotionBaseline", "showAudioOutputSettings", "playAudioOutputTest", "AudioOutputStatus.inspect", $realSmsHint, "safeTopPadding", "statusBarHeight", "setEmergencyContacts", "MAX_EMERGENCY_CONTACTS", "emergencyActionSummary", "showAssistantCheckIn", "saveAssistantCheckIn", "showAssistantCheckInDialog", "assistantCheckInSummary", "SleepWaveformView", "waveformSampleCount", "evidenceGradeFor", "evidenceLineFor", "isFormalSleepEvent", "externalDeviceTruthText", "askAssistantSleepAudioAnalysis", "sleepWaveformModelSummary", "sleepEvidenceAudioBase64", "AUDIO_ANALYSIS_MAX_BYTES", $devDrillLabel, $realWaveLabel)) {
  if ($main -notmatch [regex]::Escape($needle)) { throw "Static check failed: $needle" }
}
foreach ($needle in @("applyAssistantTtsProfileIfNeeded", "assistantTtsProfileApplied", "assistantTtsAppliedRole")) {
  if ($main -notmatch [regex]::Escape($needle)) { throw "Main TTS profile static check failed: $needle" }
}
foreach ($needle in @("createLiveAvatarStage", "AvatarView", "AvatarState", "AvatarCommand", "avatarStateForMood", "applyLiveAvatarState", "applyLiveAvatarGesture", "startAvatarSpeaking", "stopAvatarSpeaking", "updateAvatarMouthFromPcm", "pcm16Level", "avatarMoodForVisionTask", "recordLiveEmotionTagState", "safetyLevel")) {
  if ($main -notmatch [regex]::Escape($needle)) { throw "Main live avatar static check failed: $needle" }
}
foreach ($needle in @("LIVE_DOT_ROW_HEIGHT_DP", "LIVE_DOT_MAX_HEIGHT_DP", "LIVE_VOICE_METER_HEIGHT_DP", "LIVE_VOICE_METER_BAR_HEIGHT_DP", "setPivotY", "setScaleY", "looksLikeWellnessTipQuestion")) {
  if ($main -notmatch [regex]::Escape($needle)) { throw "Assistant live stability static check failed: $needle" }
}
$sleepCheckQuestion = -join ([char[]](0x60a8, 0x7761, 0x4e86, 0x4e48))
$localSleepSoundLabel = -join ([char[]](0x672c, 0x5730, 0x52a9, 0x7720, 0x97f3))
$newsSourceMissingLabel = -join ([char[]](0x65b0, 0x95fb, 0x6e90, 0x8fd8, 0x6ca1, 0x63a5, 0x5165))
foreach ($needle in @("sleepCheckPending", "maybeAskIfUserAsleep", $sleepCheckQuestion, "looksLikeStillAwakeReply", "looksLikeReadyToSleepReply", "not_asleep", "still_awake", "asleep_now", "sleepCheckContinueLine", "enterSleepGuardFromCompanion", "restoreSleepSoundAfterAwakeReply", "duckForSpeech", "restoreGentleVolume", "settleCompanionAvatarAfterSpeech", "startMonitoring", "askBedtimeStory", "localBedtimeStory", "startCompanionModelAnswer", "startHttpVoiceAnswerWithPrompt", "looksLikeBedtimeStoryRequest", "looksLikeNewsRequest", "looksLikeSleepSoundStartRequest", "looksLikeSleepSoundStopRequest", "tell_bedtime_story", "read_news", "play_rain_sound", "stop_rain_sound", "startSleepSound", "stopSleepSound", "toggleSleepSound", "SleepSoundPlayer", "fadeOutAndStop", "showNewsCapabilityStatus", $localSleepSoundLabel, $newsSourceMissingLabel)) {
  if ($main -notmatch [regex]::Escape($needle)) { throw "Assistant bedtime companion static check failed: $needle" }
}
$liveAvatarFactory = [regex]::Match($main, 'private View createLiveAvatarStage[\s\S]*?private void destroyLiveStageWebView').Value
foreach ($needle in @("new AvatarView", "AvatarCommand.setState", "setRole", "setCharacterResource", "setCharacterBitmapMode", "setOnClickListener")) {
  if ($liveAvatarFactory -notmatch [regex]::Escape($needle)) { throw "Assistant 2D avatar factory static check failed: $needle" }
}
if ($liveAvatarFactory -match 'designImage|roleLiveAssetName|startLiveAvatarMotion') {
  throw "Assistant 2D avatar factory static check failed: live stage must not use single image avatar motion"
}
$avatarState = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\avatar\AvatarState.java") -Raw
foreach ($needle in @("IDLE", "LISTENING", "USER_SPEAKING", "THINKING", "SPEAKING", "INTERRUPTED", "SEEING", "READING", "FINDING", "COMFORTING", "HAPPY", "WORRIED", "URGENT_WAKEUP", "fromMood")) {
  if ($avatarState -notmatch [regex]::Escape($needle)) { throw "AvatarState static check failed: $needle" }
}
$avatarCommand = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\avatar\AvatarCommand.java") -Raw
foreach ($needle in @("SET_STATE", "SET_EMOTION", "START_SPEAKING", "STOP_SPEAKING", "MOUTH_LEVEL", "BLINK", "NOD", "LOOK_AT_USER", "LOOK_DOWN", "WAVE", "URGENT_WAKE")) {
  if ($avatarCommand -notmatch [regex]::Escape($needle)) { throw "AvatarCommand static check failed: $needle" }
}
$avatarView = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\avatar\AvatarView.java") -Raw
foreach ($needle in @("class AvatarView extends View", "applyCommand", "setCharacterResource", "setCharacterBitmapMode", "setAnimationEnabled", "animationEnabled", "BitmapFactory", "drawCharacterBitmapStage", "drawBitmapStateCue", "drawBitmapMouthHint", "bitmapMouthYRatio", "bitmapFaceYRatio", "characterBitmapMode", "drawHead", "drawEyes", "drawBrows", "drawMouth", "drawHands", "targetMouthLevel", "mouthLevel", "blinkLevel", "targetLookX", "targetLookY", "nextAutonomousGazeAtMs", "updateAutonomousGaze", "seedStateGaze", "setGazeTarget", "AvatarCommand.MOUTH_LEVEL", "AvatarState.URGENT_WAKEUP")) {
  if ($avatarView -notmatch [regex]::Escape($needle)) { throw "AvatarView static check failed: $needle" }
}
$avatarAssets = @(
  "avatar_2d_sister.png",
  "avatar_2d_brother.png",
  "avatar_2d_young_man.png",
  "avatar_2d_gentle_woman.png"
)
foreach ($asset in $avatarAssets) {
  $assetPath = Join-Path $root "src\main\res\drawable-nodpi\$asset"
  if (-not (Test-Path $assetPath)) { throw "Avatar asset missing: $asset" }
  if ((Get-Item $assetPath).Length -lt 100000) { throw "Avatar asset appears invalid: $asset" }
}
$wellnessButtonLabel = -join ([char[]](0x5999, 0x62db))
if ($main -match ('addLiveActionButton\([^;]*"' + [regex]::Escape($wellnessButtonLabel) + '"')) {
  throw "Assistant UX static check failed: wellness tips must not be a visible live button"
}
$liveDotAnimator = [regex]::Match($main, 'private void animateLiveStateDots[\s\S]*?private void startLiveAvatarMotion').Value
if ($liveDotAnimator -match 'setLayoutParams|LayoutParams') {
  throw "Assistant live stability static check failed: dot animator must not resize layout"
}
$liveMeterAnimator = [regex]::Match($main, 'private void animateXiaozhiVoiceMeter[\s\S]*?private void interruptForUserSpeech').Value
if ($liveMeterAnimator -match 'setLayoutParams|LayoutParams|topMargin') {
  throw "Assistant live stability static check failed: voice meter animator must not resize layout"
}
$alarm = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\AlarmActivity.java") -Raw
foreach ($needle in @("CompanionAssistant", "wakeLine", "confirmLine", "assistantRole", "drillMode", "drill_mode", "AudioOutputStatus.inspect", "alarmLine", "AvatarView", "AvatarCommand.urgentWake", "AvatarState.URGENT_WAKEUP")) {
  if ($alarm -notmatch [regex]::Escape($needle)) { throw "Alarm static check failed: $needle" }
}
$companion = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\util\CompanionAssistant.java") -Raw
foreach ($needle in @("ROLE_SISTER", "ROLE_BROTHER", "ROLE_YOUNG_MAN", "ROLE_GENTLE_WOMAN", "chatSleepReport", "chatPrivacy", "visionIntro", "visionPrivacy", "visionLocalReply", "visualMemorySaved", "findObjectLine", "thinkingComfortLine", "sampleLine", "wellnessTipLine", "checkInIntro", "checkInReply", "checkInCareLine", "firstMeetingIntro", "firstMeetingDone", "companionshipPrinciples", "profileWizardIntro", "profileWizardDone")) {
  if ($companion -notmatch [regex]::Escape($needle)) { throw "Companion static check failed: $needle" }
}
$deepSeek = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\util\DeepSeekClient.java") -Raw
foreach ($needle in @("https://api.deepseek.com/chat/completions", "Authorization", "deepseek-v4-flash", "thinking", "chatWithImage", "image_url", "data:image/jpeg;base64")) {
  if ($deepSeek -notmatch [regex]::Escape($needle)) { throw "DeepSeek static check failed: $needle" }
}
$serverApi = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\util\ServerApiClient.java") -Raw
$liveAudioTrackPlaybackHint = "AudioTrack " + (-join ([char[]](0x4f4e, 0x5ef6, 0x8fdf, 0x64ad, 0x653e)))
foreach ($needle in @("requestCode", "verifyCode", "syncProfile", "uploadInsight", "chat", "careBrief", "CareBriefResult", "/api/care/brief", "vision", "audio", "health", "ServerHealth", "modelReady", "liveLine", "companionLine", "bedtimeStory", "musicPlayback", "musicVolumeBehavior", "volume_behavior", "fade_in_start_duck_during_sleep_check_restore_if_awake_fade_out_before_guard", "newsBriefing", "possibleAsleepConfirm", "possibleAsleepAwakeReplyBehavior", "awake_reply_behavior", "continue_companion_playback", "voiceShortcuts", "voice_shortcuts", "语音说故事、新闻、雨声会走真实入口", "websocketLiveSession", "xiaozhiProtocol", "modelTextStreaming", "realtimeConfigured", "serverAsrStreaming", "modelAudioOutputStreaming", "modelAudioOutputForwarding", "apkLowLatencyAudioPlayback", "apkAutoBargeInDetection", "local2dAvatarView", "avatarStateMachine", "avatarCommandProtocol", "mouthLevelProtocol", "pcmEnergyMouthDriver", "ttsTimedMouthDriver", "avatarSpeechSettle", "avatar_speech_settle", "emotionLabelProtocol", "modelEmotionTags", "live2dSdk", "apk_auto_barge_in_detection", $liveAudioTrackPlaybackHint, "pendingMessages", "markMessageRead", "exportMe", "deleteMe", "/api/me/export", "DELETE", "http://10.0.2.2:8787", "Authorization", "sms_provider", "model_used", "model_provider", "model_name", "message_id", "vision_used", "vision_provider", "audio_used", "audio_provider")) {
  if ($serverApi -notmatch [regex]::Escape($needle)) { throw "Server API static check failed: $needle" }
}
$prefs = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\util\PreferenceStore.java") -Raw
foreach ($needle in @("MAX_EMERGENCY_CONTACTS", "emergencyPhone(int index)", "emergencyPhones", "setEmergencyContacts", "emergencySummary", "emergencyActionSummary", "cleanEmergencyPhone", "emergencyPhoneValidationError", "recordLiveVoiceState", "recordCompanionShortcutRoute", "last_companion_shortcut_route", "last_companion_shortcut_source", "recordLiveAudioFrameState", "last_live_audio_frame_count", "resetLiveTtsDeltaState", "recordLiveTtsDeltaState", "last_live_tts_delta_count", "recordLiveModelAudioFrameState", "last_live_model_audio_frame_count", "recordLiveAbortState", "last_live_abort_count", "last_live_abort_realtime_aborted", "last_live_abort_realtime_source", "last_live_abort_realtime_detail", "recordLiveAutoBargeInState", "last_live_auto_barge_in_count", "last_live_auto_barge_in_frames", "last_live_auto_barge_in_speech_ms", "last_live_auto_barge_in_threshold_rms", "last_live_auto_barge_in_noise_floor_rms", "recordLiveEmotionTagState", "last_live_emotion_tag_count", "last_live_emotion_tag_intensity", "last_live_emotion_tag_gesture", "last_live_emotion_tag_safety_level", "last_live_emotion_tag_source", "recordVisionCaptureState", "last_vision_capture_source", "last_vision_capture_bytes", "last_vision_capture_width", "last_vision_capture_height", "last_vision_capture_at", "assistantCheckInToday", "setAssistantCheckIn", "assistantCheckInSummary", "assistantPersonaConfigured", "setAssistantPersona", "ownerAddress", "assistantPersonaSummary", "assistantVideoChatMode", "setAssistantVideoChatMode", "debugCompanionUiTestMode", "assistantAutoVisionEnabled", "markAssistantAutoVisionNow", "setVisualMemory", "visualMemorySummary", "markMedicationSeen", "medicationVisionSummary", "serverBaseUrl", "serverRegistered", "setServerAuth", "serverAccountSummary", "AndroidKeyStore", "deepseek_api_key_encrypted_v1", "encryptSecret", "decryptSecret", "GCMParameterSpec", "remove(LEGACY_DEEPSEEK_API_KEY)")) {
  if ($prefs -notmatch [regex]::Escape($needle)) { throw "Preference static check failed: $needle" }
}
if (-not [regex]::Match($prefs, 'setAssistantVideoChatMode[\s\S]*?commit\(\)').Success) {
  throw "Preference static check failed: assistant video/text toggle must commit synchronously"
}
$notifier = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\util\EmergencyNotifier.java") -Raw
foreach ($needle in @("String[] phones", "sendSms(activity, phones", "sendTextMessage", "call(activity, phones[0])")) {
  if ($notifier -notmatch [regex]::Escape($needle)) { throw "Emergency notifier static check failed: $needle" }
}
$db = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\data\SleepDatabase.java") -Raw
foreach ($needle in @("audio_path", "audio_summary", "motion_summary", "device_summary", "evidence_level", "doctorReportText", "device_readings", "insertDeviceReading", "nearestDeviceEvidence", "findNearestDeviceReading", "object_memory", "upsertObjectMemory", "objectMemoryAnswer", "objectMemorySummary", "clearObjectMemory", "signal_samples", "insertSignalSample", "countSignalSamplesSince", "getRecentSignalLevelsSince", "signalSummarySince", "createSignalSamplesTable")) {
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
foreach ($needle in @("MedicationReminderReceiver", "HydrationReminderReceiver", "ServerMessageReceiver", "ProactiveCareActivity", "VisionImageProvider", "grantUriPermissions", "SCHEDULE_EXACT_ALARM", "android.permission.CAMERA", "android.permission.BLUETOOTH_CONNECT", "android:launchMode=`"singleTop`"", "android:debuggable=`"true`"")) {
  if ($manifest -notmatch [regex]::Escape($needle)) { throw "Manifest check failed: $needle" }
}
$audioOutput = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\util\AudioOutputStatus.java") -Raw
foreach ($needle in @("AudioOutputStatus", "AudioManager.GET_DEVICES_OUTPUTS", "TYPE_BLUETOOTH_A2DP", "TYPE_BLUETOOTH_SCO", "TYPE_BLE_SPEAKER", "TYPE_BUILTIN_SPEAKER", "BLUETOOTH_CONNECT", "ToneGenerator", "playAlarmTestTone", "routeLine", "preSleepLine", "alarmLine")) {
  if ($audioOutput -notmatch [regex]::Escape($needle)) { throw "Audio output static check failed: $needle" }
}
$careScheduler = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\CareReminderScheduler.java") -Raw
foreach ($needle in @("ACTION_SERVER_MESSAGE_POLL", "NOTIFICATION_SERVER_MESSAGE", "scheduleNextServerMessagePoll", "scheduleServerMessagePollLater", "ServerMessageReceiver", "cancelServerMessagePoll", "ACTION_HYDRATION_LATER", "scheduleHydrationLater")) {
  if ($careScheduler -notmatch [regex]::Escape($needle)) { throw "Care scheduler static check failed: $needle" }
}
$proactiveCare = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\ProactiveCareActivity.java") -Raw
foreach ($needle in @("TextToSpeech", "TYPE_HYDRATION", "TYPE_MEDICATION", "TYPE_SERVER_MESSAGE", "EXTRA_SERVER_MESSAGE_ID", "markMessageRead", "proactiveHydrationLine", "proactiveMedicationLine", "ServerApiClient.careBrief", "modelCareContext", "ACTION_OPEN_COMPANION", "scheduleHydrationLater", "scheduleMedicationLater", "scheduleServerMessagePollLater")) {
  if ($proactiveCare -notmatch [regex]::Escape($needle)) { throw "Proactive care static check failed: $needle" }
}
$hydrationReceiver = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\HydrationReminderReceiver.java") -Raw
foreach ($needle in @("ProactiveCareActivity", "TYPE_HYDRATION", "startActivity", "ACTION_HYDRATION_LATER")) {
  if ($hydrationReceiver -notmatch [regex]::Escape($needle)) { throw "Hydration receiver static check failed: $needle" }
}
$medicationReceiver = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\MedicationReminderReceiver.java") -Raw
foreach ($needle in @("ProactiveCareActivity", "TYPE_MEDICATION", "startActivity")) {
  if ($medicationReceiver -notmatch [regex]::Escape($needle)) { throw "Medication receiver static check failed: $needle" }
}
$alarmActivity = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\AlarmActivity.java") -Raw
foreach ($needle in @("TextToSpeech", "speakWakeLine", "sleepWakeVoiceLine", "USAGE_ALARM")) {
  if ($alarmActivity -notmatch [regex]::Escape($needle)) { throw "Alarm voice static check failed: $needle" }
}
$serverMessageReceiver = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\ServerMessageReceiver.java") -Raw
foreach ($needle in @("pendingMessages", "startVoiceReader", "ProactiveCareActivity.TYPE_SERVER_MESSAGE", "companion_messages_voice_v2", "setFullScreenIntent", "IMPORTANCE_HIGH", "prefs.isMonitoring()", "priority >= 4", "setSound", "enableVibration(false)", "scheduleNextServerMessagePoll")) {
  if ($serverMessageReceiver -notmatch [regex]::Escape($needle)) { throw "Server message receiver static check failed: $needle" }
}
$e2eCare = Get-Content -Encoding UTF8 (Join-Path $root "e2e-server-care.ps1") -Raw
foreach ($needle in @("/api/care/brief", "DEBUG_SERVER_MESSAGE_POLL", "gouxiong_sleep_prefs.xml", "run-as com.gouxiong.sleep", "WaitMessageRead", "ProactiveCareActivity", "ResetPrefsToLocalServer", "ResetTempDataDir", "KeepInjectedPrefs", "GOUXIONG_DISABLE_MODEL", "DASHSCOPE_API_KEY", "ALIYUN_MODEL_API_KEY", "ALIYUN_BAILIAN_API_KEY", "uiautomator dump --compressed")) {
  if ($e2eCare -notmatch [regex]::Escape($needle)) { throw "E2E care script check failed: $needle" }
}
$e2eVoice = Get-Content -Encoding UTF8 (Join-Path $root "e2e-live-voice.ps1") -Raw
foreach ($needle in @("DEBUG_VOICE_TEXT", "debug_voice_text", "DEBUG_SLEEP_CHECK", "DEBUG_SLEEP_CHECK_TIMEOUT", "DEBUG_LIVE_BARGE_IN", "/api/live/session WebSocket", "I_woke_up_twice", "TriggerDebugVoiceText", "TriggerDebugSleepCheck", "TriggerDebugSleepCheckTimeout", "WaitLiveVoiceStage", "LatestLiveVoiceStage", "WaitCompanionShortcutRoute", "AssertVoiceShortcutRoute", "last_companion_shortcut_route", "not_asleep_continue", "asleep_now", "sleep_check_awake_reply", "sleep_check_asleep_reply", "sleep_check_no_reply", "AssertVoiceShortcutReply", "play_rain_sound", "stop_rain_sound", "read_news", "tell_bedtime_story", "voice_shortcuts", "GrantRuntimePermissions", "DismissPermissionDialogs", "READ_MEDIA_AUDIO", "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_VISUAL_USER_SELECTED", "BLUETOOTH_CONNECT", "CALL_PHONE", "SEND_SMS", "permission_allow_button", "appops set com.gouxiong.sleep", "/api/me/export", "chat_messages", "live_voice", "/api/live/session", "ALIYUN_REALTIME_ENABLED", "fake Aliyun Realtime", "response.cancel", "input_audio_buffer.clear", "WaitLiveVoiceReply", "WaitLiveAudioFrames", "WaitLiveEmotionTag", "WaitLiveModelAudioFrames", "WaitLiveAutoBargeIn", "TriggerLiveAutoBargeIn", "WaitLiveVoiceInterrupted", "WaitLogContains", "TapLiveCompanionStage", "--activity-reorder-to-front", "input tap", "last_live_audio_frame_count", "last_live_model_audio_frame_count", "last_live_emotion_tag_count", "model_reply_analysis", "last_live_auto_barge_in_count", "last_live_auto_barge_in_speech_ms", "last_live_auto_barge_in_threshold_rms", "last_live_auto_barge_in_noise_floor_rms", "last_live_abort_count", "last_live_abort_realtime_aborted", "last_live_abort_realtime_source", "server_realtime_bridge_abort", "live_audio_frames", "live_model_audio_frames", "live_emotion_tags", "live_auto_barge_in", "live_interrupt", "last_voice_state_stage", "ReadAppPrefs", "ResetPrefsToLocalServer", "ResetTempDataDir", "KeepInjectedPrefs")) {
  if ($e2eVoice -notmatch [regex]::Escape($needle)) { throw "E2E live voice script check failed: $needle" }
}
$e2eUi = Get-Content -Encoding UTF8 (Join-Path $root "e2e-assistant-ui-mode.ps1") -Raw
foreach ($needle in @("Assistant UI mode E2E", "assistant_video_chat_mode", "debug_companion_ui_test_mode", "AssertVideoMode", "AssertTextMode", "TapModeToggle", "ReadAppPrefs", "PrefHasVideoMode", "TapNodeByAttribute", "uiautomator dump --compressed", "rm -f", "gouxiong-assistant-ui-video-mode.png", "gouxiong-assistant-ui-text-mode.png", "video_mode_no_text_bubble", "text_mode_no_avatar_stage", "toggle_roundtrip", "KeepInjectedPrefs", "ResetPrefsToLocalServer")) {
  if ($e2eUi -notmatch [regex]::Escape($needle)) { throw "E2E assistant UI script check failed: $needle" }
}
$e2eAvatar = Get-Content -Encoding UTF8 (Join-Path $root "e2e-avatar-states.ps1") -Raw
foreach ($needle in @("Avatar states E2E", "DEBUG_CAMERA_GLANCE", "DEBUG_NIGHTMARE_WAKE", "PrepareDeviceUi", "WaitVisionCapture", "WaitUrgentWakeActivity", "WaitPackageStopped", "TriggerCameraGlanceWithRetry", "TriggerUrgentWakeWithRetry", "last_vision_capture_bytes", "last_vision_capture_width", "last_vision_capture_height", "debug_camera_glance", "debug_nightmare_wake", "urgent_wakeup", "com.gouxiong.sleep/.AlarmActivity", "gouxiong-avatar-camera-glance.png", "gouxiong-avatar-urgent-wakeup.png", "camera_glance_records_real_frame", "urgent_wakeup_alarm_activity_drill", "KeepInjectedPrefs", "ResetPrefsToLocalServer")) {
  if ($e2eAvatar -notmatch [regex]::Escape($needle)) { throw "E2E avatar states script check failed: $needle" }
}
$visionProvider = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\VisionImageProvider.java") -Raw
foreach ($needle in @("ContentProvider", "openFile", "MODE_WRITE_ONLY", "getCacheDir", "image/jpeg", "newImageUri")) {
  if ($visionProvider -notmatch [regex]::Escape($needle)) { throw "Vision provider static check failed: $needle" }
}
$liveProtocol = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\live\LiveCompanionProtocol.java") -Raw
foreach ($needle in @("AUDIO_FORMAT = `"pcm16`"", "AUDIO_ENCODING = `"signed_16bit_little_endian`"", "PCM_SAMPLE_RATE = 16000", "PCM_FRAME_DURATION_MS = 30", "helloMessage", "startCallMessage", "listenMessage", "abortMessage", "wake_word_detected", "voiceMuteMessage", "inputTextMessage")) {
  if ($liveProtocol -notmatch [regex]::Escape($needle)) { throw "Live protocol static check failed: $needle" }
}
$nativeWebSocket = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\live\NativeWebSocketClient.java") -Raw
foreach ($needle in @("Sec-WebSocket-Key", "Sec-WebSocket-Accept", "OPCODE_PING", "OPCODE_PONG", "sendBinary", "sendFrame", "mask", "wss", "SSLSocketFactory")) {
  if ($nativeWebSocket -notmatch [regex]::Escape($needle)) { throw "Native WebSocket static check failed: $needle" }
}
$liveSession = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\live\LiveCompanionSession.java") -Raw
foreach ($needle in @("device-id", "client-id", "protocol-version", "Authorization", "helloMessage", "startCallMessage", "listenMessage", "abortMessage", "sendPcmFrame", "sendTextInput", "onTts", "onStt", "onEmotion", "intensity", "gesture", "safety_level", "speech_text", "onAudio")) {
  if ($liveSession -notmatch [regex]::Escape($needle)) { throw "Live session static check failed: $needle" }
}
$liveRecorder = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\live\LivePcmRecorder.java") -Raw
foreach ($needle in @("SAMPLE_RATE", "FRAME_DURATION_MS", "FRAME_SAMPLES", "VOICE_COMMUNICATION", "AcousticEchoCanceler", "NoiseSuppressor", "AutomaticGainControl", "onPcmFrame", "shortsToLittleEndianPcm", "rms")) {
  if ($liveRecorder -notmatch [regex]::Escape($needle)) { throw "Live PCM recorder static check failed: $needle" }
}
$livePlayer = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\live\LivePcmPlayer.java") -Raw
foreach ($needle in @("AudioTrack", "MODE_STREAM", "ENCODING_PCM_16BIT", "CHANNEL_OUT_MONO", "USAGE_ASSISTANCE_ACCESSIBILITY", "LiveCompanionProtocol.PCM_SAMPLE_RATE", "track.write", "track.play", "track.release")) {
  if ($livePlayer -notmatch [regex]::Escape($needle)) { throw "Live PCM player static check failed: $needle" }
}
$sleepSoundPlayer = Get-Content -Encoding UTF8 (Join-Path $root "src\main\java\com\gouxiong\sleep\util\SleepSoundPlayer.java") -Raw
foreach ($needle in @("class SleepSoundPlayer", "AudioTrack", "startRain", "fadeOutAndStop", "duckForSpeech", "restoreGentleVolume", "fadeTo", "fadeSerial", "clampVolume", "USAGE_MEDIA", "CONTENT_TYPE_MUSIC", "MODE_STREAM", "Random")) {
  if ($sleepSoundPlayer -notmatch [regex]::Escape($needle)) { throw "Sleep sound player static check failed: $needle" }
}

Write-Host "== Local secret leak check =="
$apkText = [System.Text.Encoding]::ASCII.GetString([System.IO.File]::ReadAllBytes($apk))
function Assert-SecretNotInApk($label, $secret) {
  if (-not $secret) { return $false }
  $clean = $secret.Trim().Trim('"').Trim("'")
  if ($clean.Length -le 8) { return $false }
  if ($apkText.Contains($clean)) {
    throw "$label secret leaked into APK"
  }
  return $true
}
$localConfig = Join-Path $root "local.deepseek.properties"
if (Test-Path $localConfig) {
  $secret = $null
  foreach ($line in Get-Content -Encoding UTF8 $localConfig) {
    if ($line -match '^deepseek\.apiKey=(.+)$') { $secret = $Matches[1].Trim() }
  }
  if (Assert-SecretNotInApk "Local DeepSeek" $secret) {
    Write-Host "Local DeepSeek key is not present in APK."
  } else {
    Write-Host "Local config exists, but no valid key was found for leak check."
  }
} else {
  Write-Host "No local DeepSeek config found; skipping local key leak check."
}
$aliyunEnv = Join-Path $env:USERPROFILE "Desktop\aliyun-sms.env"
if (Test-Path $aliyunEnv) {
  $checkedAliyun = 0
  foreach ($line in Get-Content -Encoding UTF8 $aliyunEnv) {
    if ($line -match '^(ALIYUN_SMS_ACCESS_KEY_ID|ALIYUN_SMS_ACCESS_KEY_SECRET)=(.+)$') {
      if (Assert-SecretNotInApk "Aliyun SMS" $Matches[2]) { $checkedAliyun++ }
    }
  }
  if ($checkedAliyun -gt 0) {
    Write-Host "Aliyun SMS secrets are not present in APK."
  } else {
    Write-Host "Aliyun env exists, but no valid SMS secrets were found for leak check."
  }
} else {
  Write-Host "No Aliyun SMS env found on Desktop; skipping Aliyun leak check."
}
$projectRoot = Split-Path -Parent $root
$serverEnv = Join-Path $projectRoot "server\.env"
$desktopModelEnv = Join-Path $env:USERPROFILE "Desktop\guanlin-aliyun-ai.env"
$checkedModel = 0
$modelKeys = @("DASHSCOPE_API_KEY", "ALIYUN_MODEL_API_KEY", "ALIYUN_BAILIAN_API_KEY", "DEEPSEEK_API_KEY", "VISION_API_KEY")
foreach ($name in $modelKeys) {
  if (Assert-SecretNotInApk "Model $name" ([Environment]::GetEnvironmentVariable($name))) { $checkedModel++ }
}
if (Test-Path $serverEnv) {
  foreach ($line in Get-Content -Encoding UTF8 $serverEnv) {
    if ($line -match '^(DASHSCOPE_API_KEY|ALIYUN_MODEL_API_KEY|ALIYUN_BAILIAN_API_KEY|DEEPSEEK_API_KEY|VISION_API_KEY)=(.+)$') {
      if (Assert-SecretNotInApk "Server model" $Matches[2]) { $checkedModel++ }
    }
  }
}
if (Test-Path $desktopModelEnv) {
  foreach ($line in Get-Content -Encoding UTF8 $desktopModelEnv) {
    if ($line -match '^(DASHSCOPE_API_KEY|ALIYUN_MODEL_API_KEY|ALIYUN_BAILIAN_API_KEY|DEEPSEEK_API_KEY|VISION_API_KEY)=(.+)$') {
      if (Assert-SecretNotInApk "Desktop model" $Matches[2]) { $checkedModel++ }
    }
  }
}
if ($checkedModel -gt 0) {
  Write-Host "Configured model secrets are not present in APK."
} else {
  Write-Host "No configured model secrets found for leak check."
}

Write-Host "== Device check =="
& (Join-Path $sdk "platform-tools\adb.exe") devices -l
Write-Host "If no device is listed above, local build tests are complete but true device interaction is not covered."

Write-Host "== APK hash =="
Get-FileHash $apk -Algorithm SHA256
