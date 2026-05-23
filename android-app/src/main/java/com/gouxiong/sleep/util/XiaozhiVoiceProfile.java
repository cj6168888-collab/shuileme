package com.gouxiong.sleep.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import java.util.Locale;
import java.util.Set;

public final class XiaozhiVoiceProfile {
    public static final int SAMPLE_RATE = 16000;
    public static final int CHANNELS = 1;
    public static final int FRAME_DURATION_MS = 30;

    private XiaozhiVoiceProfile() {
    }

    public static String summary(String role) {
        role = CompanionAssistant.normalize(role);
        if (CompanionAssistant.ROLE_SISTER.equals(role)) {
            return "明亮、轻快、亲近，像家里活泼小妹妹。";
        }
        if (CompanionAssistant.ROLE_BROTHER.equals(role)) {
            return "清楚、年轻、偏男孩声，适合记事提醒。";
        }
        if (CompanionAssistant.ROLE_YOUNG_MAN.equals(role)) {
            return "低一点、稳一点、偏男青年声，适合安全提醒和复盘。";
        }
        return "柔和、慢一点、耐心，适合长期陪伴老人。";
    }

    public static void configureRealtimeAudio(Context context) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return;
        try {
            manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            manager.setSpeakerphoneOn(true);
        } catch (Exception ignored) {
        }
    }

    public static void restoreRealtimeAudio(Context context) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return;
        try {
            manager.setMode(AudioManager.MODE_NORMAL);
        } catch (Exception ignored) {
        }
    }

    public static void applyTo(TextToSpeech tts, String role) {
        if (tts == null) return;
        Profile profile = profile(role);
        tts.setLanguage(Locale.CHINA);
        tts.setPitch(profile.pitch);
        tts.setSpeechRate(profile.rate);
        if (Build.VERSION.SDK_INT >= 21) {
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            Voice voice = selectChineseVoice(tts, profile);
            if (voice != null) {
                try {
                    tts.setVoice(voice);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static Profile profile(String role) {
        role = CompanionAssistant.normalize(role);
        if (CompanionAssistant.ROLE_SISTER.equals(role)) {
            return new Profile(1.20f, 1.06f, true, "child,xiao,mei,female");
        }
        if (CompanionAssistant.ROLE_BROTHER.equals(role)) {
            return new Profile(0.84f, 1.03f, false, "boy,di,male,man,youth,xiaoming,xiaogang,yunxi,yunjian,ge");
        }
        if (CompanionAssistant.ROLE_YOUNG_MAN.equals(role)) {
            return new Profile(0.74f, 0.94f, false, "male,man,ge,yunxi,yunjian,xiaogang,xiaoming,boy");
        }
        return new Profile(1.02f, 0.90f, true, "female,woman,jie,xiaoxiao,xiaoyi");
    }

    private static Voice selectChineseVoice(TextToSpeech tts, Profile profile) {
        if (Build.VERSION.SDK_INT < 21) return null;
        Set<Voice> voices;
        try {
            voices = tts.getVoices();
        } catch (Exception ex) {
            return null;
        }
        if (voices == null || voices.isEmpty()) return null;
        Voice preferredGender = null;
        Voice neutralChinese = null;
        Voice firstChinese = null;
        for (Voice voice : voices) {
            if (voice == null || voice.getLocale() == null) continue;
            if (!"zh".equals(voice.getLocale().getLanguage())) continue;
            if (firstChinese == null) firstChinese = voice;
            String name = voice.getName() == null ? "" : voice.getName().toLowerCase(Locale.US);
            if (matchesKeywords(name, profile.keywords)) {
                return voice;
            }
            if (profile.preferFemale) {
                if (preferredGender == null && looksFemale(name)) {
                    preferredGender = voice;
                }
            } else if (preferredGender == null && looksMale(name)) {
                preferredGender = voice;
            }
            if (neutralChinese == null && !looksFemale(name) && !looksMale(name)) {
                neutralChinese = voice;
            }
        }
        if (preferredGender != null) return preferredGender;
        if (!profile.preferFemale && neutralChinese != null) return neutralChinese;
        return firstChinese;
    }

    private static boolean matchesKeywords(String name, String keywords) {
        String[] parts = keywords.split(",");
        for (String part : parts) {
            String keyword = part.trim();
            if (keyword.length() > 0 && name.contains(keyword)) return true;
        }
        return false;
    }

    private static boolean looksFemale(String name) {
        return name.contains("female")
                || name.contains("woman")
                || name.contains("girl")
                || name.contains("mei")
                || name.contains("jie")
                || name.contains("xiaoxiao")
                || name.contains("xiaoyi")
                || name.contains("xiaobei")
                || name.contains("huihui");
    }

    private static boolean looksMale(String name) {
        if (looksFemale(name)) return false;
        return name.contains("male")
                || name.contains("man")
                || name.contains("boy")
                || name.contains("youth")
                || name.contains("ge")
                || name.contains("di")
                || name.contains("yunxi")
                || name.contains("yunjian")
                || name.contains("xiaoming")
                || name.contains("xiaogang");
    }

    private static final class Profile {
        final float pitch;
        final float rate;
        final boolean preferFemale;
        final String keywords;

        Profile(float pitch, float rate, boolean preferFemale, String keywords) {
            this.pitch = pitch;
            this.rate = rate;
            this.preferFemale = preferFemale;
            this.keywords = keywords;
        }
    }
}
