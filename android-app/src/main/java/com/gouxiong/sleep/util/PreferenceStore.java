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

    public boolean assistantOnlineEnabled() {
        return prefs.getBoolean("assistant_online_enabled", false);
    }

    public void setAssistantOnlineEnabled(boolean value) {
        prefs.edit().putBoolean("assistant_online_enabled", value).apply();
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
