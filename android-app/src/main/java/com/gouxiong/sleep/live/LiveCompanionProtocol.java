package com.gouxiong.sleep.live;

import org.json.JSONException;
import org.json.JSONObject;

public final class LiveCompanionProtocol {
    public static final String AUDIO_FORMAT = "pcm16";
    public static final String AUDIO_ENCODING = "signed_16bit_little_endian";
    public static final int PCM_SAMPLE_RATE = 16000;
    public static final int PCM_CHANNELS = 1;
    public static final int PCM_FRAME_DURATION_MS = 30;

    private LiveCompanionProtocol() {
    }

    public static String helloMessage() {
        try {
            return new JSONObject()
                    .put("type", "hello")
                    .put("version", 1)
                    .put("transport", "websocket")
                    .put("audio_params", audioParams())
                    .toString();
        } catch (JSONException ex) {
            return "{}";
        }
    }

    public static String startCallMessage() {
        try {
            return new JSONObject()
                    .put("type", "start")
                    .put("mode", "auto")
                    .put("audio_params", audioParams())
                    .toString();
        } catch (JSONException ex) {
            return "{}";
        }
    }

    private static JSONObject audioParams() throws JSONException {
        return new JSONObject()
                .put("format", AUDIO_FORMAT)
                .put("encoding", AUDIO_ENCODING)
                .put("sample_rate", PCM_SAMPLE_RATE)
                .put("channels", PCM_CHANNELS)
                .put("frame_duration", PCM_FRAME_DURATION_MS)
                .put("container", "raw");
    }

    public static String listenMessage(String sessionId, boolean start, String mode) {
        try {
            return baseSessionMessage(sessionId, "listen")
                    .put("state", start ? "start" : "stop")
                    .put("mode", cleanMode(mode))
                    .toString();
        } catch (JSONException ex) {
            return "{}";
        }
    }

    public static String abortMessage(String sessionId) {
        try {
            return baseSessionMessage(sessionId, "abort")
                    .put("reason", "wake_word_detected")
                    .toString();
        } catch (JSONException ex) {
            return "{}";
        }
    }

    public static String voiceMuteMessage(boolean muted) {
        try {
            return new JSONObject()
                    .put("type", muted ? "voice_mute" : "voice_unmute")
                    .toString();
        } catch (JSONException ex) {
            return "{}";
        }
    }

    public static String inputTextMessage(String sessionId, String text) {
        try {
            return baseSessionMessage(sessionId, "input_text")
                    .put("text", text == null ? "" : text.trim())
                    .toString();
        } catch (JSONException ex) {
            return "{}";
        }
    }

    private static JSONObject baseSessionMessage(String sessionId, String type) throws JSONException {
        JSONObject json = new JSONObject().put("type", type);
        if (sessionId != null && sessionId.trim().length() > 0) {
            json.put("session_id", sessionId.trim());
        }
        return json;
    }

    private static String cleanMode(String mode) {
        if ("manual".equals(mode)) return "manual";
        return "auto";
    }
}
