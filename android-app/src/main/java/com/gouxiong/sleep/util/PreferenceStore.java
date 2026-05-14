package com.gouxiong.sleep.util;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceStore {
    private static final String NAME = "gouxiong_sleep_prefs";

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
        return prefs.getBoolean("emergency_enabled", false);
    }

    public String emergencyPhone() {
        return prefs.getString("emergency_phone", "");
    }

    public boolean emergencyCall() {
        return prefs.getBoolean("emergency_call", true);
    }

    public boolean emergencySms() {
        return prefs.getBoolean("emergency_sms", true);
    }

    public void setEmergency(String phone, boolean call, boolean sms) {
        prefs.edit()
                .putBoolean("emergency_enabled", phone != null && phone.trim().length() > 0)
                .putString("emergency_phone", phone == null ? "" : phone.trim())
                .putBoolean("emergency_call", call)
                .putBoolean("emergency_sms", sms)
                .apply();
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

    public boolean assistantOnlineEnabled() {
        return prefs.getBoolean("assistant_online_enabled", false);
    }

    public void setAssistantOnlineEnabled(boolean value) {
        prefs.edit().putBoolean("assistant_online_enabled", value).apply();
    }

    public String deepSeekApiKey() {
        return prefs.getString("deepseek_api_key", "");
    }

    public void setDeepSeekApiKey(String value) {
        prefs.edit().putString("deepseek_api_key", clean(value)).apply();
    }

    public boolean deepSeekKeyConfigured() {
        return deepSeekApiKey().startsWith("sk-") && deepSeekApiKey().length() > 20;
    }

    public String deepSeekModel() {
        return prefs.getString("deepseek_model", "deepseek-v4-flash");
    }

    public void setDeepSeekModel(String model) {
        String clean = clean(model);
        prefs.edit().putString("deepseek_model", clean.length() > 0 ? clean : "deepseek-v4-flash").apply();
    }

    public void clearDeepSeekApiKey() {
        prefs.edit().remove("deepseek_api_key").apply();
    }

    public boolean assistantProactiveCareEnabled() {
        return prefs.getBoolean("assistant_proactive_care_enabled", true);
    }

    public void setAssistantProactiveCareEnabled(boolean value) {
        prefs.edit().putBoolean("assistant_proactive_care_enabled", value).apply();
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

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private void appendProfileLine(StringBuilder b, String label, String value) {
        if (value != null && value.length() > 0) {
            if (b.length() > 0) b.append("\n");
            b.append(label).append("：").append(value);
        }
    }
}
