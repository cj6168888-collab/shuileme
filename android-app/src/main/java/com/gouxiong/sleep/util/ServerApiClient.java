package com.gouxiong.sleep.util;

import com.gouxiong.sleep.BuildSettings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ServerApiClient {
    private ServerApiClient() {
    }

    public static CodeResult requestCode(String baseUrl, String phone) throws Exception {
        JSONObject body = new JSONObject().put("phone", phone);
        JSONObject response = request(baseUrl, "POST", "/api/auth/request-code", body, "");
        return new CodeResult(
                response.optString("message", "验证码已发送"),
                response.optString("dev_code", ""),
                response.optString("sms_provider", ""));
    }

    public static AuthResult verifyCode(String baseUrl, String phone, String code, String deviceId) throws Exception {
        JSONObject body = new JSONObject()
                .put("phone", phone)
                .put("code", code)
                .put("device_id", deviceId == null ? "" : deviceId);
        JSONObject response = request(baseUrl, "POST", "/api/auth/verify", body, "");
        return new AuthResult(
                response.optString("token", ""),
                response.optString("phone", phone),
                response.optInt("user_id", 0));
    }

    public static void syncProfile(String baseUrl, String token, JSONObject profile) throws Exception {
        request(baseUrl, "PUT", "/api/profile", profile == null ? new JSONObject() : profile, token);
    }

    public static void uploadInsight(String baseUrl, String token, String source, String category, String content, int severity, JSONObject metadata) throws Exception {
        JSONObject body = new JSONObject()
                .put("source", source == null ? "app" : source)
                .put("content", content == null ? "" : content);
        if (category != null && category.trim().length() > 0) {
            body.put("category", category.trim());
        }
        if (severity > 0) {
            body.put("severity", severity);
        }
        body.put("metadata", metadata == null ? new JSONObject() : metadata);
        request(baseUrl, "POST", "/api/insights", body, token);
    }

    public static String chat(String baseUrl, String token, String systemPrompt, String userPrompt, String model) throws Exception {
        JSONObject body = new JSONObject()
                .put("message", userPrompt == null ? "" : userPrompt)
                .put("system_prompt", systemPrompt == null ? "" : systemPrompt)
                .put("model", model == null ? "" : model);
        JSONObject response = request(baseUrl, "POST", "/api/chat", body, token);
        String answer = response.optString("answer", "").trim();
        if (answer.length() == 0) {
            throw new IllegalStateException("服务端没有返回回答");
        }
        String provider = response.optString("model_provider", "");
        if (!response.optBoolean("model_used", true)) {
            answer += "\n\n说明：服务端当前未配置阿里多模态模型 Key，这次是兜底建议。";
        }
        return answer;
    }

    public static CareBriefResult careBrief(String baseUrl, String token, String type, JSONObject context, boolean createMessage, String model) throws Exception {
        JSONObject body = new JSONObject()
                .put("type", type == null ? "hydration" : type)
                .put("context", context == null ? new JSONObject() : context)
                .put("create_message", createMessage)
                .put("model", model == null ? "" : model);
        JSONObject response = request(baseUrl, "POST", "/api/care/brief", body, token);
        String line = response.optString("body", "").trim();
        if (line.length() == 0) {
            throw new IllegalStateException("服务端没有返回主动关怀话术");
        }
        return new CareBriefResult(
                response.optString("type", type == null ? "hydration" : type),
                response.optString("title", "小助手提醒"),
                line,
                response.optInt("message_id", 0),
                response.optBoolean("model_used", true),
                response.optString("model_provider", ""),
                response.optString("model_name", ""));
    }

    public static NewsBriefResult newsBrief(String baseUrl, String token) throws Exception {
        JSONObject response = request(baseUrl, "GET", "/api/news/brief", null, token);
        return new NewsBriefResult(
                response.optBoolean("configured", false),
                response.optString("provider", ""),
                response.optString("source_title", ""),
                response.optString("body", ""),
                response.optJSONArray("items") == null ? new JSONArray() : response.optJSONArray("items"));
    }

    public static String vision(String baseUrl, String token, String systemPrompt, String prompt, String task, String jpegBase64, String model) throws Exception {
        JSONObject body = new JSONObject()
                .put("task", task == null ? "vision" : task)
                .put("prompt", prompt == null ? "" : prompt)
                .put("image_base64", jpegBase64 == null ? "" : jpegBase64)
                .put("system_prompt", systemPrompt == null ? "" : systemPrompt)
                .put("model", model == null ? "" : model);
        JSONObject response = request(baseUrl, "POST", "/api/vision", body, token);
        String answer = response.optString("answer", "").trim();
        if (answer.length() == 0) {
            throw new IllegalStateException("服务端没有返回看图结果");
        }
        String provider = response.optString("vision_provider", "");
        if (!response.optBoolean("vision_used", true)) {
            answer += "\n\n说明：服务端当前未配置阿里多模态视觉模型，这次没有完成真实看图分析。";
        }
        return answer;
    }

    public static String audio(String baseUrl, String token, String systemPrompt, String prompt, String task, String waveformSummary, String audioBase64, String audioFormat, String model) throws Exception {
        JSONObject body = new JSONObject()
                .put("task", task == null ? "sleep_audio" : task)
                .put("prompt", prompt == null ? "" : prompt)
                .put("waveform_summary", waveformSummary == null ? "" : waveformSummary)
                .put("audio_data", audioBase64 == null ? "" : audioBase64)
                .put("audio_format", audioFormat == null ? "wav" : audioFormat)
                .put("system_prompt", systemPrompt == null ? "" : systemPrompt)
                .put("model", model == null ? "" : model);
        JSONObject response = request(baseUrl, "POST", "/api/audio", body, token);
        String answer = response.optString("answer", "").trim();
        if (answer.length() == 0) {
            throw new IllegalStateException("服务端没有返回声波分析结果");
        }
        String provider = response.optString("audio_provider", "");
        if (!response.optBoolean("audio_used", true)) {
            answer += "\n\n模型未配置，以上是兜底提醒。";
        }
        return answer;
    }

    public static JSONArray pendingMessages(String baseUrl, String token) throws Exception {
        JSONObject response = request(baseUrl, "GET", "/api/messages/pending", null, token);
        return response.optJSONArray("messages") == null ? new JSONArray() : response.optJSONArray("messages");
    }

    public static void markMessageRead(String baseUrl, String token, int messageId) throws Exception {
        request(baseUrl, "POST", "/api/messages/" + messageId + "/read", new JSONObject(), token);
    }

    public static JSONObject exportMe(String baseUrl, String token) throws Exception {
        return request(baseUrl, "GET", "/api/me/export", null, token);
    }

    public static void deleteMe(String baseUrl, String token) throws Exception {
        request(baseUrl, "DELETE", "/api/me", null, token);
    }

    public static ServerHealth health(String baseUrl) throws Exception {
        JSONObject response = request(baseUrl, "GET", "/health", null, "");
        return ServerHealth.from(response);
    }

    public static AvatarStatus avatarStatus(String baseUrl, String token) throws Exception {
        JSONObject response = request(baseUrl, "GET", "/api/avatar/status", null, token);
        JSONObject linly = response.optJSONObject("linly_digital_human");
        JSONObject linlyHealth = linly == null ? null : linly.optJSONObject("health");
        JSONObject local2d = response.optJSONObject("two_d_avatar");
        return new AvatarStatus(
                linly != null && linly.optBoolean("configured", false),
                linly == null ? "" : linly.optString("provider", ""),
                linly == null ? "" : linly.optString("transport", ""),
                linly == null ? "" : linly.optString("avatar_engine", ""),
                linly == null ? "" : linly.optString("base_url", ""),
                linly == null ? "" : linly.optString("web_url", ""),
                linly != null && linly.optBoolean("live", false),
                linlyHealth == null ? "" : linlyHealth.optString("error", ""),
                local2d != null && local2d.optBoolean("local_2d_avatar_view", false),
                local2d != null && local2d.optBoolean("avatar_state_machine", false),
                local2d != null && local2d.optBoolean("mouth_level_protocol", false));
    }

    public static AvatarOfferResult avatarOffer(String baseUrl, String token, String sdp, String type) throws Exception {
        JSONObject body = new JSONObject()
                .put("sdp", sdp == null ? "" : sdp)
                .put("type", type == null ? "offer" : type);
        JSONObject response = request(baseUrl, "POST", "/api/avatar/session/offer", body, token);
        return new AvatarOfferResult(
                response.optString("sdp", ""),
                response.optString("type", ""),
                response.optInt("sessionid", 0),
                response.optString("provider", ""),
                response.optString("avatar_engine", ""));
    }

    public static String avatarSay(String baseUrl, String token, int sessionId, String text, boolean interrupt) throws Exception {
        JSONObject body = new JSONObject()
                .put("text", text == null ? "" : text)
                .put("interrupt", interrupt);
        JSONObject response = request(baseUrl, "POST", "/api/avatar/session/" + Math.max(0, sessionId) + "/say", body, token);
        return response.optString("response", "");
    }

    public static void avatarStop(String baseUrl, String token, int sessionId) throws Exception {
        request(baseUrl, "POST", "/api/avatar/session/" + Math.max(0, sessionId) + "/stop", new JSONObject(), token);
    }

    public static boolean avatarSpeaking(String baseUrl, String token, int sessionId) throws Exception {
        JSONObject response = request(baseUrl, "GET", "/api/avatar/session/" + Math.max(0, sessionId) + "/speaking", null, token);
        return response.optBoolean("data", false);
    }

    private static JSONObject request(String baseUrl, String method, String path, JSONObject body, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(normalizeBaseUrl(baseUrl) + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(45000);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (token != null && token.trim().length() > 0) {
            connection.setRequestProperty("Authorization", "Bearer " + token.trim());
        }
        if (body != null) {
            connection.setDoOutput(true);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
            writer.write(body.toString());
            writer.flush();
            writer.close();
        }
        int code = connection.getResponseCode();
        String raw = readAll(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
        connection.disconnect();
        JSONObject response = raw.length() == 0 ? new JSONObject() : new JSONObject(raw);
        if (code < 200 || code >= 300 || !response.optBoolean("ok", true)) {
            throw new IllegalStateException(response.optString("error", "服务端请求失败：" + code));
        }
        return response;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String clean = baseUrl == null ? "" : baseUrl.trim();
        if (clean.length() == 0) {
            clean = BuildSettings.DEFAULT_SERVER_BASE_URL;
        }
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuilder b = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            b.append(line);
        }
        reader.close();
        return b.toString();
    }

    public static final class CodeResult {
        public final String message;
        public final String devCode;
        public final String smsProvider;

        CodeResult(String message, String devCode, String smsProvider) {
            this.message = message;
            this.devCode = devCode;
            this.smsProvider = smsProvider;
        }
    }

    public static final class AuthResult {
        public final String token;
        public final String phone;
        public final int userId;

        AuthResult(String token, String phone, int userId) {
            this.token = token;
            this.phone = phone;
            this.userId = userId;
        }
    }

    public static final class CareBriefResult {
        public final String type;
        public final String title;
        public final String body;
        public final int messageId;
        public final boolean modelUsed;
        public final String modelProvider;
        public final String modelName;

        CareBriefResult(String type, String title, String body, int messageId, boolean modelUsed, String modelProvider, String modelName) {
            this.type = type;
            this.title = title;
            this.body = body;
            this.messageId = messageId;
            this.modelUsed = modelUsed;
            this.modelProvider = modelProvider;
            this.modelName = modelName;
        }
    }

    public static final class NewsBriefResult {
        public final boolean configured;
        public final String provider;
        public final String sourceTitle;
        public final String body;
        public final JSONArray items;

        private NewsBriefResult(boolean configured, String provider, String sourceTitle, String body, JSONArray items) {
            this.configured = configured;
            this.provider = provider;
            this.sourceTitle = sourceTitle;
            this.body = body;
            this.items = items;
        }
    }

    public static final class AvatarStatus {
        public final boolean linlyConfigured;
        public final String provider;
        public final String transport;
        public final String avatarEngine;
        public final String baseUrl;
        public final String webUrl;
        public final boolean live;
        public final String healthError;
        public final boolean local2dAvatarView;
        public final boolean avatarStateMachine;
        public final boolean mouthLevelProtocol;

        private AvatarStatus(boolean linlyConfigured, String provider, String transport, String avatarEngine, String baseUrl, String webUrl,
                             boolean live, String healthError,
                             boolean local2dAvatarView, boolean avatarStateMachine, boolean mouthLevelProtocol) {
            this.linlyConfigured = linlyConfigured;
            this.provider = provider == null ? "" : provider;
            this.transport = transport == null ? "" : transport;
            this.avatarEngine = avatarEngine == null ? "" : avatarEngine;
            this.baseUrl = baseUrl == null ? "" : baseUrl;
            this.webUrl = webUrl == null || webUrl.length() == 0 ? this.baseUrl : webUrl;
            this.live = live;
            this.healthError = healthError == null ? "" : healthError;
            this.local2dAvatarView = local2dAvatarView;
            this.avatarStateMachine = avatarStateMachine;
            this.mouthLevelProtocol = mouthLevelProtocol;
        }

        public String line() {
            if (linlyConfigured) {
                return live
                        ? "Linly 数字人已配置且在线：" + avatarEngine + " / " + transport
                        : "Linly 数字人已配置但未连通：" + (healthError.length() > 0 ? healthError : "health failed");
            }
            if (local2dAvatarView && avatarStateMachine) {
                return "Linly 未配置，当前使用本机 2D Avatar 兜底。";
            }
            return "数字人能力未完整证明。";
        }
    }

    public static final class AvatarOfferResult {
        public final String sdp;
        public final String type;
        public final int sessionId;
        public final String provider;
        public final String avatarEngine;

        private AvatarOfferResult(String sdp, String type, int sessionId, String provider, String avatarEngine) {
            this.sdp = sdp == null ? "" : sdp;
            this.type = type == null ? "" : type;
            this.sessionId = sessionId;
            this.provider = provider == null ? "" : provider;
            this.avatarEngine = avatarEngine == null ? "" : avatarEngine;
        }
    }

    public static final class ServerHealth {
        public final boolean ok;
        public final String service;
        public final String smsProvider;
        public final boolean smsAliyunConfigured;
        public final boolean smsDevMode;
        public final String modelProvider;
        public final boolean aliyunModelConfigured;
        public final boolean textChat;
        public final boolean imageUnderstanding;
        public final boolean audioUnderstanding;
        public final boolean avatarConfigured;
        public final boolean local2dAvatarView;
        public final boolean avatarStateMachine;
        public final boolean avatarCommandProtocol;
        public final boolean mouthLevelProtocol;
        public final boolean pcmEnergyMouthDriver;
        public final boolean ttsTimedMouthDriver;
        public final boolean avatarSpeechSettle;
        public final boolean emotionLabelProtocol;
        public final boolean modelEmotionTags;
        public final boolean live2dSdk;
        public final boolean websocketLiveSession;
        public final boolean xiaozhiProtocol;
        public final boolean fallbackTextTurns;
        public final boolean modelTextStreaming;
        public final boolean realtimeConfigured;
        public final boolean serverAsrStreaming;
        public final boolean modelAudioOutputStreaming;
        public final boolean modelAudioOutputForwarding;
        public final boolean apkLowLatencyAudioPlayback;
        public final boolean apkAutoBargeInDetection;
        public final boolean digitalHumanSession;
        public final boolean digitalHumanStream;
        public final boolean linlyDigitalHumanConfigured;
        public final String linlyDigitalHumanProvider;
        public final String linlyDigitalHumanTransport;
        public final String linlyDigitalHumanAvatarEngine;
        public final String linlyDigitalHumanBaseUrl;
        public final String linlyDigitalHumanWebUrl;
        public final boolean bedtimeStory;
        public final boolean musicPlayback;
        public final String musicVolumeBehavior;
        public final boolean newsBriefing;
        public final boolean possibleAsleepConfirm;
        public final String possibleAsleepAwakeReplyBehavior;
        public final boolean voiceShortcuts;

        private ServerHealth(boolean ok, String service, String smsProvider, boolean smsAliyunConfigured, boolean smsDevMode,
                              String modelProvider, boolean aliyunModelConfigured, boolean textChat,
                              boolean imageUnderstanding, boolean audioUnderstanding, boolean avatarConfigured,
                              boolean local2dAvatarView, boolean avatarStateMachine, boolean avatarCommandProtocol,
                              boolean mouthLevelProtocol, boolean pcmEnergyMouthDriver, boolean ttsTimedMouthDriver,
                              boolean avatarSpeechSettle, boolean emotionLabelProtocol, boolean modelEmotionTags, boolean live2dSdk,
                              boolean websocketLiveSession, boolean xiaozhiProtocol, boolean fallbackTextTurns,
                              boolean modelTextStreaming, boolean realtimeConfigured,
                              boolean serverAsrStreaming, boolean modelAudioOutputStreaming,
                              boolean modelAudioOutputForwarding, boolean apkLowLatencyAudioPlayback,
                              boolean apkAutoBargeInDetection, boolean digitalHumanSession, boolean digitalHumanStream,
                              boolean linlyDigitalHumanConfigured, String linlyDigitalHumanProvider,
                              String linlyDigitalHumanTransport, String linlyDigitalHumanAvatarEngine,
                              String linlyDigitalHumanBaseUrl, String linlyDigitalHumanWebUrl, boolean bedtimeStory,
                              boolean musicPlayback, String musicVolumeBehavior,
                              boolean newsBriefing, boolean possibleAsleepConfirm,
                              String possibleAsleepAwakeReplyBehavior, boolean voiceShortcuts) {
            this.ok = ok;
            this.service = service;
            this.smsProvider = smsProvider;
            this.smsAliyunConfigured = smsAliyunConfigured;
            this.smsDevMode = smsDevMode;
            this.modelProvider = modelProvider;
            this.aliyunModelConfigured = aliyunModelConfigured;
            this.textChat = textChat;
            this.imageUnderstanding = imageUnderstanding;
            this.audioUnderstanding = audioUnderstanding;
            this.avatarConfigured = avatarConfigured;
            this.local2dAvatarView = local2dAvatarView;
            this.avatarStateMachine = avatarStateMachine;
            this.avatarCommandProtocol = avatarCommandProtocol;
            this.mouthLevelProtocol = mouthLevelProtocol;
            this.pcmEnergyMouthDriver = pcmEnergyMouthDriver;
            this.ttsTimedMouthDriver = ttsTimedMouthDriver;
            this.avatarSpeechSettle = avatarSpeechSettle;
            this.emotionLabelProtocol = emotionLabelProtocol;
            this.modelEmotionTags = modelEmotionTags;
            this.live2dSdk = live2dSdk;
            this.websocketLiveSession = websocketLiveSession;
            this.xiaozhiProtocol = xiaozhiProtocol;
            this.fallbackTextTurns = fallbackTextTurns;
            this.modelTextStreaming = modelTextStreaming;
            this.realtimeConfigured = realtimeConfigured;
            this.serverAsrStreaming = serverAsrStreaming;
            this.modelAudioOutputStreaming = modelAudioOutputStreaming;
            this.modelAudioOutputForwarding = modelAudioOutputForwarding;
            this.apkLowLatencyAudioPlayback = apkLowLatencyAudioPlayback;
            this.apkAutoBargeInDetection = apkAutoBargeInDetection;
            this.digitalHumanSession = digitalHumanSession;
            this.digitalHumanStream = digitalHumanStream;
            this.linlyDigitalHumanConfigured = linlyDigitalHumanConfigured;
            this.linlyDigitalHumanProvider = linlyDigitalHumanProvider == null ? "" : linlyDigitalHumanProvider;
            this.linlyDigitalHumanTransport = linlyDigitalHumanTransport == null ? "" : linlyDigitalHumanTransport;
            this.linlyDigitalHumanAvatarEngine = linlyDigitalHumanAvatarEngine == null ? "" : linlyDigitalHumanAvatarEngine;
            this.linlyDigitalHumanBaseUrl = linlyDigitalHumanBaseUrl == null ? "" : linlyDigitalHumanBaseUrl;
            this.linlyDigitalHumanWebUrl = linlyDigitalHumanWebUrl == null || linlyDigitalHumanWebUrl.length() == 0 ? this.linlyDigitalHumanBaseUrl : linlyDigitalHumanWebUrl;
            this.bedtimeStory = bedtimeStory;
            this.musicPlayback = musicPlayback;
            this.musicVolumeBehavior = musicVolumeBehavior == null ? "" : musicVolumeBehavior;
            this.newsBriefing = newsBriefing;
            this.possibleAsleepConfirm = possibleAsleepConfirm;
            this.possibleAsleepAwakeReplyBehavior = possibleAsleepAwakeReplyBehavior == null ? "" : possibleAsleepAwakeReplyBehavior;
            this.voiceShortcuts = voiceShortcuts;
        }

        static ServerHealth from(JSONObject root) {
            JSONObject sms = root.optJSONObject("sms");
            JSONObject model = root.optJSONObject("model");
            JSONObject implemented = model == null ? null : model.optJSONObject("implemented");
            JSONObject live = model == null ? null : model.optJSONObject("live");
            JSONObject linly = model == null ? null : model.optJSONObject("linly_digital_human");
            JSONObject companion = root.optJSONObject("companion");
            JSONObject story = companion == null ? null : companion.optJSONObject("bedtime_story");
            JSONObject music = companion == null ? null : companion.optJSONObject("music_playback");
            JSONObject news = companion == null ? null : companion.optJSONObject("news_briefing");
            JSONObject asleep = companion == null ? null : companion.optJSONObject("possible_asleep_confirm");
            JSONObject shortcuts = companion == null ? null : companion.optJSONObject("voice_shortcuts");
            return new ServerHealth(
                    root.optBoolean("ok", false),
                    root.optString("service", ""),
                    sms == null ? "" : sms.optString("provider", ""),
                    sms != null && sms.optBoolean("aliyun_configured", false),
                    sms != null && sms.optBoolean("dev_sms", false),
                    model == null ? "" : model.optString("provider", ""),
                    model != null && model.optBoolean("aliyun_configured", false),
                    implemented != null && implemented.optBoolean("text_chat", false),
                    implemented != null && implemented.optBoolean("image_understanding", false),
                    implemented != null && implemented.optBoolean("audio_understanding", false),
                    model != null && model.optBoolean("avatar_configured", false),
                    implemented != null && implemented.optBoolean("local_2d_avatar_view", false),
                    implemented != null && implemented.optBoolean("avatar_state_machine", false),
                    implemented != null && implemented.optBoolean("avatar_command_protocol", false),
                    implemented != null && implemented.optBoolean("mouth_level_protocol", false),
                    implemented != null && implemented.optBoolean("pcm_energy_mouth_driver", false),
                    implemented != null && implemented.optBoolean("tts_timed_mouth_driver", false),
                    implemented != null && implemented.optBoolean("avatar_speech_settle", false),
                    implemented != null && implemented.optBoolean("emotion_label_protocol", false),
                    implemented != null && implemented.optBoolean("model_emotion_tags", false),
                    implemented != null && implemented.optBoolean("live2d_sdk", false),
                    implemented != null && implemented.optBoolean("websocket_live_session", false),
                    implemented != null && implemented.optBoolean("xiaozhi_protocol", false),
                    implemented != null && implemented.optBoolean("fallback_text_turns", false),
                    implemented != null && implemented.optBoolean("model_text_streaming", live != null && live.optBoolean("model_text_streaming", false)),
                    implemented != null && implemented.optBoolean("realtime_configured", live != null && live.optBoolean("realtime_configured", false)),
                    implemented != null && implemented.optBoolean("server_asr_streaming", live != null && live.optBoolean("server_asr_streaming", false)),
                    implemented != null && implemented.optBoolean("model_audio_output_streaming", live != null && live.optBoolean("model_audio_output_streaming", false)),
                    implemented != null && implemented.optBoolean("model_audio_output_forwarding", live != null && live.optBoolean("model_audio_output_forwarding", false)),
                    implemented != null && implemented.optBoolean("apk_low_latency_audio_playback", live != null && live.optBoolean("apk_low_latency_audio_playback", false)),
                    implemented != null && implemented.optBoolean("apk_auto_barge_in_detection", live != null && live.optBoolean("apk_auto_barge_in_detection", false)),
                    implemented != null && implemented.optBoolean("digital_human_session", live != null && live.optBoolean("digital_human_session", false)),
                    implemented != null && implemented.optBoolean("digital_human_stream", live != null && live.optBoolean("digital_human_stream", false)),
                    linly != null && linly.optBoolean("configured", false),
                    linly == null ? "" : linly.optString("provider", ""),
                    linly == null ? "" : linly.optString("transport", ""),
                    linly == null ? "" : linly.optString("avatar_engine", ""),
                    linly == null ? "" : linly.optString("base_url", ""),
                    linly == null ? "" : linly.optString("web_url", ""),
                    story != null && story.optBoolean("implemented", false),
                    music != null && music.optBoolean("implemented", false),
                    music == null ? "" : music.optString("volume_behavior", ""),
                    news != null && news.optBoolean("implemented", false),
                    asleep != null && asleep.optBoolean("implemented", false),
                    asleep == null ? "" : asleep.optString("awake_reply_behavior", ""),
                    shortcuts != null && shortcuts.optBoolean("implemented", false));
        }

        public boolean modelReady() {
            return aliyunModelConfigured && textChat && imageUnderstanding && audioUnderstanding;
        }

        public String smsLine() {
            if ("aliyun".equals(smsProvider)) return "阿里短信已配置，验证码会走真实短信。";
            if (smsDevMode) return "当前是开发短信模式，会返回测试验证码，不能用于正式用户。";
            return "短信服务未配置，手机号登录不可用。";
        }

        public String modelLine() {
            if (modelReady()) return "阿里多模态已配置：聊天、看图和声波分析可以走真实模型。";
            if (aliyunModelConfigured) return "阿里 Key 已配置，但部分能力未标记可用，请检查模型名和服务端日志。";
            return "阿里多模态 Key 未配置：聊天、看图和声波分析会使用兜底提醒。";
        }

        public String avatarLine() {
            if (linlyDigitalHumanConfigured) {
                return "Linly-Talker-Stream 数字人媒体层已配置："
                        + empty(linlyDigitalHumanAvatarEngine, "未知引擎")
                        + " / " + empty(linlyDigitalHumanTransport, "未知传输")
                        + "。它只负责数字人音视频，睡眠安全、记忆和陪伴大脑仍在本 App 与服务端。";
            }
            if (local2dAvatarView && avatarStateMachine && mouthLevelProtocol) {
                String live2d = live2dSdk ? "Live2D SDK 已接入。" : "Live2D SDK 未接入，当前是本机分层 2D Avatar。";
                String emotion = modelEmotionTags ? "模型情绪标签已接入。" : "模型情绪 JSON 尚未接入，先用场景和服务端 emotion 事件兜底。";
                String settle = avatarSpeechSettle ? "播报结束会回到真实陪伴状态。" : "播报结束状态回落仍需检查。";
                return "2D Avatar 已接入：状态机、指令协议、嘴型驱动和插话状态可用。" + settle + live2d + emotion;
            }
            return avatarConfigured
                    ? "云数字人服务已配置，但 APK 本机 2D Avatar 状态机仍需检查。"
                    : "数字人服务未配置，且本机 2D Avatar 能力未完整上报。";
        }

        public String liveLine() {
            if (!websocketLiveSession) {
                return "实时陪伴 WebSocket 未启用，当前只能用系统语音识别 + TTS 兜底。";
            }
            if (serverAsrStreaming && modelAudioOutputStreaming && apkLowLatencyAudioPlayback && apkAutoBargeInDetection) {
                return "实时陪伴已接阿里 Realtime：服务端可流式听音和回传模型音频，APK 已用 AudioTrack 低延迟播放，并具备保守自动插话打断；仍需真机验证回音消除和长时稳定。";
            }
            if (serverAsrStreaming && modelAudioOutputStreaming && apkLowLatencyAudioPlayback) {
                return "实时陪伴已接阿里 Realtime：服务端可流式听音和回传模型音频，APK 已用 AudioTrack 低延迟播放；仍需真机验证回音消除和长时稳定。";
            }
            if (serverAsrStreaming && modelAudioOutputStreaming) {
                return "实时陪伴已接阿里 Realtime：服务端可流式听音和回传模型音频；APK 播放链路还需检查。";
            }
            if (serverAsrStreaming && modelAudioOutputForwarding && !apkLowLatencyAudioPlayback) {
                return "实时陪伴已接阿里 Realtime：服务端可转发 PCM16 做流式 ASR，并能把模型音频帧回传；APK 低延迟播放仍未完成。";
            }
            if (modelTextStreaming) {
                return "小智式 WebSocket 会话已具备：支持文本流式回复和 PCM16 音频帧接收；流式 ASR 与模型音频输出仍未接通。";
            }
            if (xiaozhiProtocol && fallbackTextTurns) {
                return "小智式 WebSocket 会话已具备：支持 hello/start/listen/abort、文本轮次和 PCM16 音频帧接收；真正流式 ASR 与模型音频输出仍未接通。";
            }
            return "实时陪伴 WebSocket 已启用，但协议能力不完整，请检查服务端。";
        }

        public String digitalHumanLine() {
            if (linlyDigitalHumanConfigured) {
                return "Linly 数字人流已配置：" + empty(linlyDigitalHumanAvatarEngine, "avatar")
                        + "，APK 后续可通过 /api/avatar/session/offer 建立 WebRTC。";
            }
            if (digitalHumanSession) {
                return "数字人会话已标记可用，但 Linly Stream 未配置，可能是其他云数字人预留通道。";
            }
            return "Linly 数字人流未配置，当前使用本机 2D Avatar 兜底。";
        }

        public String companionLine() {
            StringBuilder b = new StringBuilder();
            b.append(bedtimeStory ? "睡前故事可用" : "睡前故事未接入");
            b.append("；");
            if (musicPlayback) {
                b.append("本地助眠音已接入，版权音乐平台未接入");
                if ("fade_in_start_duck_during_sleep_check_restore_if_awake_fade_out_before_guard".equals(musicVolumeBehavior)) {
                    b.append("，会淡入、询问时压低、转守护前淡出");
                }
            } else {
                b.append("音乐播放未接入，不会假装播放");
            }
            b.append("；");
            b.append(newsBriefing ? "新闻简报已接入" : "新闻源未接入，不会编新闻");
            b.append("；");
            if (possibleAsleepConfirm) {
                b.append("会轻声确认“您睡了么？”再转守护");
                if ("continue_companion_playback".equals(possibleAsleepAwakeReplyBehavior)) {
                    b.append("，没睡会继续陪伴");
                }
            } else {
                b.append("可能入睡确认未接入");
            }
            b.append("；");
            b.append(voiceShortcuts ? "语音说故事、新闻、雨声会走真实入口" : "自然语音入口仍需检查");
            return b.toString();
        }

        private static String empty(String value, String fallback) {
            return value == null || value.length() == 0 ? fallback : value;
        }
    }
}
