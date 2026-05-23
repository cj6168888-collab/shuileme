package com.gouxiong.sleep.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class PreferenceStore {
    private static final String NAME = "gouxiong_sleep_prefs";
    private static final String LEGACY_DEEPSEEK_API_KEY = "deepseek_api_key";
    private static final String ENCRYPTED_DEEPSEEK_API_KEY = "deepseek_api_key_encrypted_v1";
    private static final String DEEPSEEK_KEYSTORE_ALIAS = "gouxiong_sleep_deepseek_api_key";
    public static final int MAX_EMERGENCY_CONTACTS = 3;

    private final SharedPreferences prefs;

    public PreferenceStore(Context context) {
        prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public boolean isFirstLaunch() {
        return prefs.getBoolean("first_launch", true);
    }

    public void setFirstLaunchDone() {
        prefs.edit().putBoolean("first_launch", false).apply();
    }

    public void recordLiveVoiceState(String stage, String title, String body) {
        prefs.edit()
                .putString("last_voice_state_stage", clean(stage))
                .putString("last_voice_state_title", clean(title))
                .putString("last_voice_state_body", clean(body))
                .putLong("last_voice_state_at", System.currentTimeMillis())
                .apply();
    }

    public void recordCompanionShortcutRoute(String route, String source) {
        prefs.edit()
                .putString("last_companion_shortcut_route", clean(route))
                .putString("last_companion_shortcut_source", clean(source))
                .putLong("last_companion_shortcut_at", System.currentTimeMillis())
                .apply();
    }

    public void recordLiveAudioFrameState(int frameCount, String source, float rms) {
        prefs.edit()
                .putInt("last_live_audio_frame_count", Math.max(0, frameCount))
                .putString("last_live_audio_frame_source", clean(source))
                .putFloat("last_live_audio_frame_rms", Math.max(0f, rms))
                .putLong("last_live_audio_frame_at", System.currentTimeMillis())
                .apply();
    }

    public void resetLiveTtsDeltaState(int serial) {
        prefs.edit()
                .putInt("last_live_tts_delta_count", 0)
                .putInt("last_live_tts_delta_serial", Math.max(0, serial))
                .putString("last_live_tts_delta_text", "")
                .putLong("last_live_tts_delta_at", 0L)
                .apply();
    }

    public void recordLiveTtsDeltaState(int serial, String text) {
        int count = prefs.getInt("last_live_tts_delta_count", 0) + 1;
        prefs.edit()
                .putInt("last_live_tts_delta_count", Math.max(1, count))
                .putInt("last_live_tts_delta_serial", Math.max(0, serial))
                .putString("last_live_tts_delta_text", clean(text))
                .putLong("last_live_tts_delta_at", System.currentTimeMillis())
                .apply();
    }

    public void recordLiveModelAudioFrameState(int frameCount, int bytes, String source) {
        prefs.edit()
                .putInt("last_live_model_audio_frame_count", Math.max(0, frameCount))
                .putInt("last_live_model_audio_frame_bytes", Math.max(0, bytes))
                .putString("last_live_model_audio_frame_source", clean(source))
                .putLong("last_live_model_audio_frame_at", System.currentTimeMillis())
                .apply();
    }

    public void recordLiveAbortState(String source, boolean realtimeAborted, String detail) {
        int count = prefs.getInt("last_live_abort_count", 0) + 1;
        boolean stickyRealtimeAborted = realtimeAborted || prefs.getBoolean("last_live_abort_realtime_aborted", false);
        SharedPreferences.Editor editor = prefs.edit()
                .putInt("last_live_abort_count", Math.max(1, count))
                .putString("last_live_abort_source", clean(source))
                .putBoolean("last_live_abort_realtime_aborted", stickyRealtimeAborted)
                .putString("last_live_abort_detail", clean(detail))
                .putLong("last_live_abort_at", System.currentTimeMillis());
        if (realtimeAborted) {
            editor.putString("last_live_abort_realtime_source", clean(source))
                    .putString("last_live_abort_realtime_detail", clean(detail));
        }
        editor.apply();
    }

    public void recordLiveAutoBargeInState(float rms, int consecutiveFrames, float thresholdRms, float noiseFloorRms, int speechMs) {
        int count = prefs.getInt("last_live_auto_barge_in_count", 0) + 1;
        prefs.edit()
                .putInt("last_live_auto_barge_in_count", Math.max(1, count))
                .putFloat("last_live_auto_barge_in_rms", Math.max(0f, rms))
                .putInt("last_live_auto_barge_in_frames", Math.max(0, consecutiveFrames))
                .putInt("last_live_auto_barge_in_speech_ms", Math.max(0, speechMs))
                .putFloat("last_live_auto_barge_in_threshold_rms", Math.max(0f, thresholdRms))
                .putFloat("last_live_auto_barge_in_noise_floor_rms", Math.max(0f, noiseFloorRms))
                .putLong("last_live_auto_barge_in_at", System.currentTimeMillis())
                .apply();
    }

    public void recordLiveEmotionTagState(String emotion, float intensity, String gesture, String safetyLevel, String speechText, String source) {
        int count = prefs.getInt("last_live_emotion_tag_count", 0) + 1;
        prefs.edit()
                .putInt("last_live_emotion_tag_count", Math.max(1, count))
                .putString("last_live_emotion_tag_emotion", clean(emotion))
                .putFloat("last_live_emotion_tag_intensity", Math.max(0f, Math.min(1f, intensity)))
                .putString("last_live_emotion_tag_gesture", clean(gesture))
                .putString("last_live_emotion_tag_safety_level", clean(safetyLevel))
                .putString("last_live_emotion_tag_speech_text", clean(speechText))
                .putString("last_live_emotion_tag_source", clean(source))
                .putLong("last_live_emotion_tag_at", System.currentTimeMillis())
                .apply();
    }

    public void recordVisionCaptureState(String source, int bytes, int width, int height) {
        prefs.edit()
                .putString("last_vision_capture_source", clean(source))
                .putInt("last_vision_capture_bytes", Math.max(0, bytes))
                .putInt("last_vision_capture_width", Math.max(0, width))
                .putInt("last_vision_capture_height", Math.max(0, height))
                .putLong("last_vision_capture_at", System.currentTimeMillis())
                .apply();
    }

    public void recordSleepAudioReadState(int readCount, double rms, int peak) {
        prefs.edit()
                .putInt("last_sleep_audio_read_count", Math.max(0, readCount))
                .putFloat("last_sleep_audio_read_rms", (float) Math.max(0d, rms))
                .putInt("last_sleep_audio_read_peak", Math.max(0, peak))
                .putLong("last_sleep_audio_read_at", System.currentTimeMillis())
                .apply();
    }

    public void recordMicrophoneProbeState(boolean started, int frames, float maxRms, float minRms, String error) {
        prefs.edit()
                .putBoolean("last_microphone_probe_started", started)
                .putInt("last_microphone_probe_frames", Math.max(0, frames))
                .putFloat("last_microphone_probe_max_rms", Math.max(0f, maxRms))
                .putFloat("last_microphone_probe_min_rms", Math.max(0f, minRms))
                .putString("last_microphone_probe_error", clean(error))
                .putLong("last_microphone_probe_at", System.currentTimeMillis())
                .apply();
    }

    public void recordSpeechRecognitionState(String stage, String text, int errorCode) {
        prefs.edit()
                .putString("last_speech_recognition_stage", clean(stage))
                .putString("last_speech_recognition_text", clean(text))
                .putInt("last_speech_recognition_error", errorCode)
                .putLong("last_speech_recognition_at", System.currentTimeMillis())
                .apply();
    }

    public boolean speechRecognitionPassed() {
        return prefs.getString("last_speech_recognition_text", "").trim().length() > 0;
    }

    public String speechRecognitionShortState() {
        String text = prefs.getString("last_speech_recognition_text", "");
        String stage = prefs.getString("last_speech_recognition_stage", "");
        int error = prefs.getInt("last_speech_recognition_error", 0);
        if (text != null && text.trim().length() > 0) {
            return text.trim();
        }
        if ("error".equals(stage) && error > 0) {
            return "错误 " + error;
        }
        if (stage != null && stage.length() > 0) {
            return stage;
        }
        return "未证明听懂";
    }

    public String microphoneProbeSummary() {
        long at = prefs.getLong("last_microphone_probe_at", 0L);
        if (at <= 0L) {
            return "还没有做过现场拾音验证。权限打开不等于真的采到声音。";
        }
        boolean started = prefs.getBoolean("last_microphone_probe_started", false);
        int frames = prefs.getInt("last_microphone_probe_frames", 0);
        float maxRms = prefs.getFloat("last_microphone_probe_max_rms", 0f);
        float minRms = prefs.getFloat("last_microphone_probe_min_rms", 0f);
        String error = prefs.getString("last_microphone_probe_error", "");
        StringBuilder b = new StringBuilder();
        b.append("AudioRecord 启动：").append(started ? "是" : "否").append("\n");
        b.append("PCM 帧数：").append(frames).append("\n");
        b.append("RMS 范围：").append(String.format(java.util.Locale.US, "%.4f - %.4f", minRms, maxRms)).append("\n");
        if (error != null && error.length() > 0) {
            b.append("错误：").append(error).append("\n");
        }
        b.append("结论：").append(microphoneProbePassed() ? "已证明本机能采到非静音声音。" : "未证明真实拾音；不能把麦克风能力标成已完成。");
        return b.toString();
    }

    public boolean microphoneProbePassed() {
        int frames = prefs.getInt("last_microphone_probe_frames", 0);
        float maxRms = prefs.getFloat("last_microphone_probe_max_rms", 0f);
        float minRms = prefs.getFloat("last_microphone_probe_min_rms", 0f);
        String error = prefs.getString("last_microphone_probe_error", "");
        return prefs.getBoolean("last_microphone_probe_started", false)
                && frames >= 12
                && maxRms >= 0.010f
                && maxRms - minRms >= 0.004f
                && (error == null || error.length() == 0);
    }

    public String mode() {
        return prefs.getString("mode", "标准模式");
    }

    public void setMode(String mode) {
        prefs.edit().putString("mode", mode).apply();
    }

    public boolean isMonitoring() {
        return prefs.getBoolean("monitoring", false);
    }

    public void setMonitoring(boolean value) {
        prefs.edit().putBoolean("monitoring", value).apply();
    }

    public long lastHeartbeat() {
        return prefs.getLong("last_heartbeat", 0L);
    }

    public void markHeartbeat() {
        prefs.edit().putLong("last_heartbeat", System.currentTimeMillis()).apply();
    }

    public double signalAudioBaselineRms() {
        return Double.longBitsToDouble(prefs.getLong("signal_audio_baseline_rms", Double.doubleToLongBits(1800.0)));
    }

    public double signalMotionBaseline() {
        return Double.longBitsToDouble(prefs.getLong("signal_motion_baseline", Double.doubleToLongBits(2.0)));
    }

    public int signalBaselineSamples() {
        return prefs.getInt("signal_baseline_samples", 0);
    }

    public void setSignalBaseline(double audioRms, double motion, int samples) {
        prefs.edit()
                .putLong("signal_audio_baseline_rms", Double.doubleToLongBits(audioRms))
                .putLong("signal_motion_baseline", Double.doubleToLongBits(motion))
                .putInt("signal_baseline_samples", samples)
                .apply();
    }

    public boolean morningPromptedToday() {
        return dayKey(System.currentTimeMillis()).equals(prefs.getString("morning_prompt_date", ""));
    }

    public void markMorningPromptedToday() {
        prefs.edit().putString("morning_prompt_date", dayKey(System.currentTimeMillis())).apply();
    }

    private String dayKey(long timeMillis) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(timeMillis);
        return c.get(java.util.Calendar.YEAR) + "-" + c.get(java.util.Calendar.DAY_OF_YEAR);
    }

    public boolean saveAudioClips() {
        return prefs.getBoolean("save_audio_clips", true);
    }

    public void setSaveAudioClips(boolean value) {
        prefs.edit().putBoolean("save_audio_clips", value).apply();
    }

    public boolean emergencyEnabled() {
        return prefs.getBoolean("emergency_enabled", false) && emergencyPhones().length > 0;
    }

    public String emergencyPhone() {
        return emergencyPhone(0);
    }

    public String emergencyPhone(int index) {
        if (index < 0 || index >= MAX_EMERGENCY_CONTACTS) return "";
        String value = prefs.getString("emergency_phone_" + (index + 1), "");
        if (index == 0 && value.length() == 0) {
            value = prefs.getString("emergency_phone", "");
        }
        return clean(value);
    }

    public String[] emergencyPhones() {
        java.util.ArrayList<String> phones = new java.util.ArrayList<>();
        for (int i = 0; i < MAX_EMERGENCY_CONTACTS; i++) {
            String phone = emergencyPhone(i);
            if (phone.length() > 0 && !phones.contains(phone)) {
                phones.add(phone);
            }
        }
        return phones.toArray(new String[0]);
    }

    public String emergencySummary() {
        String[] phones = emergencyPhones();
        if (phones.length == 0) {
            return "未设置";
        }
        return phones.length + " 位联系人：" + joinPhones(phones);
    }

    public boolean emergencyCall() {
        return prefs.getBoolean("emergency_call", true);
    }

    public boolean emergencySms() {
        return prefs.getBoolean("emergency_sms", true);
    }

    public void setEmergency(String phone, boolean call, boolean sms) {
        setEmergencyContacts(new String[]{phone}, call, sms);
    }

    public void setEmergencyContacts(String[] phones, boolean call, boolean sms) {
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean("emergency_call", call)
                .putBoolean("emergency_sms", sms);
        String first = "";
        int saved = 0;
        for (int i = 0; i < MAX_EMERGENCY_CONTACTS; i++) {
            String phone = "";
            if (phones != null && i < phones.length) {
                phone = cleanEmergencyPhone(phones[i]);
            }
            if (phone.length() > 0) {
                saved++;
                if (first.length() == 0) first = phone;
            }
            editor.putString("emergency_phone_" + (i + 1), phone);
        }
        editor
                .putBoolean("emergency_enabled", saved > 0)
                .putString("emergency_phone", first)
                .apply();
    }

    public static String cleanEmergencyPhone(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= '0' && c <= '9') {
                b.append(c);
            } else if (c == '+' && b.length() == 0) {
                b.append(c);
            }
        }
        return b.toString();
    }

    public static String emergencyPhoneValidationError(String[] phones) {
        if (phones == null) return null;
        for (int i = 0; i < phones.length && i < MAX_EMERGENCY_CONTACTS; i++) {
            String raw = phones[i] == null ? "" : phones[i].trim();
            if (raw.length() == 0) continue;
            String phone = cleanEmergencyPhone(raw);
            int digits = phone.startsWith("+") ? phone.length() - 1 : phone.length();
            if (digits < 6 || digits > 20) {
                return "第 " + (i + 1) + " 位联系人号码长度不正确，请检查后再保存";
            }
        }
        return null;
    }

    public String emergencyActionSummary() {
        if (!emergencyEnabled()) {
            return "未启用";
        }
        if (emergencyCall() && emergencySms()) return "短信通知全部联系人，并拨打第 1 位联系人";
        if (emergencySms()) return "短信通知全部联系人";
        if (emergencyCall()) return "拨打第 1 位联系人";
        return "已保存联系人，但未开启电话或短信";
    }

    private String joinPhones(String[] phones) {
        StringBuilder b = new StringBuilder();
        for (String phone : phones) {
            if (b.length() > 0) b.append("，");
            b.append(phone);
        }
        return b.toString();
    }

    public String alarmSource() {
        return prefs.getString("alarm_source", "system");
    }

    public String voicePath() {
        return prefs.getString("voice_path", "");
    }

    public String songUri() {
        return prefs.getString("song_uri", "");
    }

    public String alarmLabel() {
        String source = alarmSource();
        if ("voice".equals(source) && voicePath().length() > 0) {
            return "亲人录音";
        }
        if ("song".equals(source) && songUri().length() > 0) {
            return "本地歌曲";
        }
        return "系统闹钟";
    }

    public void useVoice(String path) {
        prefs.edit()
                .putString("alarm_source", "voice")
                .putString("voice_path", path == null ? "" : path)
                .apply();
    }

    public void useSong(String uri) {
        prefs.edit()
                .putString("alarm_source", "song")
                .putString("song_uri", uri == null ? "" : uri)
                .apply();
    }

    public void useSystemAlarm() {
        prefs.edit().putString("alarm_source", "system").apply();
    }

    public boolean externalDeviceEnabled() {
        return prefs.getBoolean("external_device_enabled", false);
    }

    public String externalDeviceName() {
        return prefs.getString("external_device_name", "");
    }

    public String externalDeviceSummary() {
        return prefs.getString("external_device_summary", "");
    }

    public void setExternalDevice(String name, String summary) {
        String cleanName = name == null ? "" : name.trim();
        String cleanSummary = summary == null ? "" : summary.trim();
        prefs.edit()
                .putBoolean("external_device_enabled", cleanName.length() > 0 || cleanSummary.length() > 0)
                .putString("external_device_name", cleanName)
                .putString("external_device_summary", cleanSummary)
                .apply();
    }

    public void clearExternalDevice() {
        prefs.edit()
                .putBoolean("external_device_enabled", false)
                .putString("external_device_name", "")
                .putString("external_device_summary", "")
                .apply();
    }

    public String externalDeviceEvidence() {
        if (!externalDeviceEnabled()) {
            return "可穿戴/外部设备：未接入，未参与本次判断";
        }
        String name = externalDeviceName().length() > 0 ? externalDeviceName() : "外部设备";
        String summary = externalDeviceSummary().length() > 0 ? externalDeviceSummary() : "已标记有外部设备，但暂无心率/血氧/呼吸率摘要";
        return "可穿戴/外部设备：" + name + "；" + summary + "。当前版本仅保存用户录入摘要，尚未自动校验原始数据";
    }

    public String companionRole() {
        return prefs.getString("companion_role", "温柔姐姐");
    }

    public void setCompanionRole(String role) {
        prefs.edit().putString("companion_role", role == null ? "温柔姐姐" : role).apply();
    }

    public boolean assistantVideoChatMode() {
        return prefs.getBoolean("assistant_video_chat_mode", true);
    }

    public void setAssistantVideoChatMode(boolean enabled) {
        prefs.edit().putBoolean("assistant_video_chat_mode", enabled).commit();
    }

    public boolean debugCompanionUiTestMode() {
        return prefs.getBoolean("debug_companion_ui_test_mode", false);
    }

    public boolean assistantPersonaConfigured() {
        return prefs.getBoolean("assistant_persona_configured", false);
    }

    public String assistantName() {
        String name = prefs.getString("assistant_name", "");
        return name.length() > 0 ? name : "小熊";
    }

    public String assistantIdentity() {
        String identity = prefs.getString("assistant_identity", "");
        return identity.length() > 0 ? identity : "听话贴心的小助理";
    }

    public String ownerAddress() {
        String address = prefs.getString("owner_address", "");
        return address.length() > 0 ? address : "主人";
    }

    public void setAssistantPersona(String name, String identity) {
        setAssistantPersona(name, identity, ownerAddress());
    }

    public void setAssistantPersona(String name, String identity, String ownerAddress) {
        String cleanName = clean(name);
        String cleanIdentity = clean(identity);
        String cleanAddress = clean(ownerAddress);
        prefs.edit()
                .putBoolean("assistant_persona_configured", true)
                .putString("assistant_name", cleanName.length() > 0 ? cleanName : "小熊")
                .putString("assistant_identity", cleanIdentity.length() > 0 ? cleanIdentity : "听话贴心的小助理")
                .putString("owner_address", cleanAddress.length() > 0 ? cleanAddress : "主人")
                .apply();
    }

    public String assistantPersonaSummary() {
        if (!assistantPersonaConfigured()) {
            return "还没有正式认识。第一次聊天时，主人可以给小助手起名字、定身份。";
        }
        return "名字：" + assistantName() + "\n身份：" + assistantIdentity() + "\n称呼您：" + ownerAddress();
    }

    public String serverBaseUrl() {
        String value = prefs.getString("server_base_url", "http://10.0.2.2:8787");
        return value == null || value.trim().length() == 0 ? "http://10.0.2.2:8787" : value.trim();
    }

    public void setServerBaseUrl(String value) {
        String clean = clean(value);
        if (clean.length() == 0) clean = "http://10.0.2.2:8787";
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        prefs.edit().putString("server_base_url", clean).apply();
    }

    public String serverAuthToken() {
        return prefs.getString("server_auth_token", "");
    }

    public String serverPhone() {
        return prefs.getString("server_phone", "");
    }

    public int serverUserId() {
        return prefs.getInt("server_user_id", 0);
    }

    public boolean serverRegistered() {
        return serverAuthToken().length() > 20 && serverPhone().length() > 0;
    }

    public void setServerAuth(String phone, String token, int userId) {
        prefs.edit()
                .putString("server_phone", clean(phone))
                .putString("server_auth_token", clean(token))
                .putInt("server_user_id", userId)
                .apply();
    }

    public void clearServerAuth() {
        prefs.edit()
                .remove("server_phone")
                .remove("server_auth_token")
                .remove("server_user_id")
                .apply();
    }

    public String serverAccountSummary() {
        if (serverRegistered()) {
            return "已登录：" + serverPhone()
                    + "\n小助手长期记忆已开启。"
                    + "\n会自动同步档案、聊天重点和提醒消息。";
        }
        return "还没登录。输入手机号验证码后，小助手会记住健康、用药、睡眠和家庭情况。";
    }

    public boolean assistantOnlineEnabled() {
        return true;
    }

    public void setAssistantOnlineEnabled(boolean value) {
        prefs.edit().putBoolean("assistant_online_enabled", true).apply();
    }

    public String deepSeekApiKey() {
        String encrypted = prefs.getString(ENCRYPTED_DEEPSEEK_API_KEY, "");
        if (encrypted.length() > 0) {
            try {
                return decryptSecret(encrypted);
            } catch (Exception ignored) {
                return "";
            }
        }
        String legacy = prefs.getString(LEGACY_DEEPSEEK_API_KEY, "");
        if (legacy.length() > 0) {
            if (setDeepSeekApiKey(legacy)) {
                prefs.edit().remove(LEGACY_DEEPSEEK_API_KEY).apply();
            }
            return clean(legacy);
        }
        return "";
    }

    public boolean setDeepSeekApiKey(String value) {
        String clean = clean(value);
        if (clean.length() == 0) {
            clearDeepSeekApiKey();
            return true;
        }
        try {
            prefs.edit()
                    .putString(ENCRYPTED_DEEPSEEK_API_KEY, encryptSecret(clean))
                    .remove(LEGACY_DEEPSEEK_API_KEY)
                    .apply();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean deepSeekKeyConfigured() {
        return serverRegistered();
    }

    public boolean localDeepSeekKeyConfigured() {
        return false;
    }

    public String deepSeekModel() {
        return prefs.getString("deepseek_model", "deepseek-v4-flash");
    }

    public void setDeepSeekModel(String model) {
        String clean = clean(model);
        prefs.edit().putString("deepseek_model", clean.length() > 0 ? clean : "deepseek-v4-flash").apply();
    }

    public void clearDeepSeekApiKey() {
        prefs.edit()
                .remove(ENCRYPTED_DEEPSEEK_API_KEY)
                .remove(LEGACY_DEEPSEEK_API_KEY)
                .apply();
    }

    public boolean assistantProactiveCareEnabled() {
        return prefs.getBoolean("assistant_proactive_care_enabled", true);
    }

    public void setAssistantProactiveCareEnabled(boolean value) {
        prefs.edit().putBoolean("assistant_proactive_care_enabled", value).apply();
    }

    public boolean assistantAutoVisionEnabled() {
        return prefs.getBoolean("assistant_auto_vision_enabled", true);
    }

    public void setAssistantAutoVisionEnabled(boolean value) {
        prefs.edit().putBoolean("assistant_auto_vision_enabled", value).apply();
    }

    public long assistantAutoVisionLastAt() {
        return prefs.getLong("assistant_auto_vision_last_at", 0L);
    }

    public void markAssistantAutoVisionNow() {
        prefs.edit().putLong("assistant_auto_vision_last_at", System.currentTimeMillis()).apply();
    }

    public String healthProfile() {
        return prefs.getString("owner_health_profile", "");
    }

    public String medicationHabits() {
        return prefs.getString("owner_medication_habits", "");
    }

    public String sleepSituation() {
        return prefs.getString("owner_sleep_situation", "");
    }

    public String familySituation() {
        return prefs.getString("owner_family_situation", "");
    }

    public String hobbies() {
        return prefs.getString("owner_hobbies", "");
    }

    public String carePreference() {
        return prefs.getString("owner_care_preference", "");
    }

    public void setOwnerProfile(String health, String medication, String sleep, String family, String hobbies, String care) {
        prefs.edit()
                .putString("owner_health_profile", clean(health))
                .putString("owner_medication_habits", clean(medication))
                .putString("owner_sleep_situation", clean(sleep))
                .putString("owner_family_situation", clean(family))
                .putString("owner_hobbies", clean(hobbies))
                .putString("owner_care_preference", clean(care))
                .apply();
    }

    public boolean ownerProfileStarted() {
        return healthProfile().length() > 0
                || medicationHabits().length() > 0
                || sleepSituation().length() > 0
                || familySituation().length() > 0
                || hobbies().length() > 0
                || carePreference().length() > 0;
    }

    public String ownerProfileSummary() {
        if (!ownerProfileStarted()) {
            return "还没有填写主人档案。填写后，小助手会更懂你的身体状况、用药习惯、睡眠情况、家庭情况和兴趣爱好。";
        }
        StringBuilder b = new StringBuilder();
        appendProfileLine(b, "身体状况", healthProfile());
        appendProfileLine(b, "用药习惯", medicationHabits());
        appendProfileLine(b, "睡眠情况", sleepSituation());
        appendProfileLine(b, "家庭情况", familySituation());
        appendProfileLine(b, "兴趣爱好", hobbies());
        appendProfileLine(b, "关怀偏好", carePreference());
        return b.toString();
    }

    public boolean assistantCarePromptedToday() {
        return dayKey(System.currentTimeMillis()).equals(prefs.getString("assistant_care_prompt_date", ""));
    }

    public void markAssistantCarePromptedToday() {
        prefs.edit().putString("assistant_care_prompt_date", dayKey(System.currentTimeMillis())).apply();
    }

    public boolean assistantCheckInToday() {
        return dayKey(System.currentTimeMillis()).equals(prefs.getString("assistant_checkin_date", ""));
    }

    public String assistantCheckInMood() {
        return prefs.getString("assistant_checkin_mood", "");
    }

    public String assistantCheckInEnergy() {
        return prefs.getString("assistant_checkin_energy", "");
    }

    public String assistantCheckInNote() {
        return prefs.getString("assistant_checkin_note", "");
    }

    public void setAssistantCheckIn(String mood, String energy, String note) {
        prefs.edit()
                .putString("assistant_checkin_date", dayKey(System.currentTimeMillis()))
                .putString("assistant_checkin_mood", clean(mood))
                .putString("assistant_checkin_energy", clean(energy))
                .putString("assistant_checkin_note", clean(note))
                .apply();
    }

    public String assistantCheckInSummary() {
        if (!assistantCheckInToday()) {
            return "今天还没有记录状态。点“记录今天状态”，小助手会更懂今天该怎么关心你。";
        }
        StringBuilder b = new StringBuilder();
        appendProfileLine(b, "心情", assistantCheckInMood());
        appendProfileLine(b, "精力", assistantCheckInEnergy());
        appendProfileLine(b, "补充", assistantCheckInNote());
        if (b.length() == 0) {
            return "今天已记录状态，但没有填写细节。";
        }
        return b.toString();
    }

    public boolean medicationEnabled() {
        return prefs.getBoolean("medication_enabled", false);
    }

    public String medicationName() {
        return prefs.getString("medication_name", "早晨用药");
    }

    public int medicationRepeatMinutes() {
        return prefs.getInt("medication_repeat_minutes", 30);
    }

    public long medicationConfirmedAt() {
        return prefs.getLong("medication_confirmed_at", 0L);
    }

    public void setMedication(String name, int repeatMinutes) {
        prefs.edit()
                .putBoolean("medication_enabled", name != null && name.trim().length() > 0)
                .putString("medication_name", name == null ? "" : name.trim())
                .putInt("medication_repeat_minutes", repeatMinutes)
                .apply();
    }

    public void confirmMedicationNow() {
        prefs.edit().putLong("medication_confirmed_at", System.currentTimeMillis()).apply();
    }

    public boolean medicationConfirmedToday() {
        long confirmed = medicationConfirmedAt();
        if (confirmed <= 0) return false;
        java.util.Calendar a = java.util.Calendar.getInstance();
        java.util.Calendar b = java.util.Calendar.getInstance();
        b.setTimeInMillis(confirmed);
        return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR)
                && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR);
    }

    public boolean hydrationReminderEnabled() {
        return prefs.getBoolean("hydration_reminder_enabled", true);
    }

    public void setHydrationReminderEnabled(boolean enabled) {
        prefs.edit().putBoolean("hydration_reminder_enabled", enabled).apply();
    }

    public long hydrationAcknowledgedAt() {
        return prefs.getLong("hydration_acknowledged_at", 0L);
    }

    public void markHydrationAcknowledgedNow() {
        prefs.edit().putLong("hydration_acknowledged_at", System.currentTimeMillis()).apply();
    }

    public String importantObjectMemory() {
        return prefs.getString("assistant_important_object_memory", "");
    }

    public void setVisualMemory(String item, String place, String note) {
        String cleanItem = clean(item);
        String cleanPlace = clean(place);
        String cleanNote = clean(note);
        if (cleanItem.length() == 0 && cleanPlace.length() == 0 && cleanNote.length() == 0) {
            return;
        }
        String line = dateTime(System.currentTimeMillis()) + "："
                + (cleanItem.length() == 0 ? "重要东西" : cleanItem)
                + " 放在 "
                + (cleanPlace.length() == 0 ? "主人描述的位置" : cleanPlace);
        if (cleanNote.length() > 0) {
            line += "；备注：" + cleanNote;
        }
        String existing = importantObjectMemory();
        String combined = line + (existing.length() > 0 ? "\n" + existing : "");
        prefs.edit().putString("assistant_important_object_memory", limitLines(combined, 8)).apply();
    }

    public void clearVisualMemory() {
        prefs.edit().remove("assistant_important_object_memory").apply();
    }

    public String visualMemorySummary() {
        String memory = importantObjectMemory();
        if (memory.length() == 0) {
            return "还没有记东西位置。可以让小助手帮你记：钥匙、眼镜、药盒、存折、手机充电器等放在哪里。";
        }
        return memory;
    }

    public long medicationSeenAt() {
        return prefs.getLong("assistant_medication_seen_at", 0L);
    }

    public String medicationSeenNote() {
        return prefs.getString("assistant_medication_seen_note", "");
    }

    public void markMedicationSeen(String note) {
        prefs.edit()
                .putLong("assistant_medication_seen_at", System.currentTimeMillis())
                .putString("assistant_medication_seen_note", clean(note))
                .apply();
    }

    public String medicationVisionSummary() {
        long seenAt = medicationSeenAt();
        if (seenAt <= 0) {
            return "还没有拍照记录吃药。小助手可以看药盒、药杯或主人确认动作，但不会判断药量、换药或停药。";
        }
        String note = medicationSeenNote();
        return dateTime(seenAt) + " 记录过一次吃药相关场景"
                + (note.length() > 0 ? "：" + note : "。")
                + "\n今天吃过后仍建议点“确认已吃药”，这样我就不会反复提醒。";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String limitLines(String value, int maxLines) {
        String[] lines = value.split("\\n");
        StringBuilder b = new StringBuilder();
        int limit = Math.min(maxLines, lines.length);
        for (int i = 0; i < limit; i++) {
            if (lines[i].trim().length() == 0) continue;
            if (b.length() > 0) b.append("\n");
            b.append(lines[i].trim());
        }
        return b.toString();
    }

    private String dateTime(long millis) {
        return new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
                .format(new java.util.Date(millis));
    }

    private String encryptSecret(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deepSeekSecretKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private String decryptSecret(String blob) throws Exception {
        String[] parts = blob.split(":", 2);
        if (parts.length != 2) return "";
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deepSeekSecretKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKey deepSeekSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(DEEPSEEK_KEYSTORE_ALIAS)) {
            KeyStore.Entry entry = keyStore.getEntry(DEEPSEEK_KEYSTORE_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                DEEPSEEK_KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }

    private void appendProfileLine(StringBuilder b, String label, String value) {
        if (value != null && value.length() > 0) {
            if (b.length() > 0) b.append("\n");
            b.append(label).append("：").append(value);
        }
    }
}
