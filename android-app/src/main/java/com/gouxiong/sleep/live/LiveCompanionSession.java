package com.gouxiong.sleep.live;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LiveCompanionSession {
    private NativeWebSocketClient client;
    private Listener listener;
    private volatile String sessionId;

    public interface Listener {
        void onConnected();

        void onTts(String state, String text);

        void onStt(String text);

        void onEmotion(String emotion, float intensity, String gesture, String safetyLevel, String speechText, JSONObject event);

        void onEvent(String type, JSONObject event);

        void onAudio(byte[] pcmFrame);

        void onClosed();

        void onError(String message, Throwable error);
    }

    public synchronized void connect(String endpoint, String token, String deviceId, String clientId, Listener liveListener) {
        if (client != null && client.isOpen()) {
            throw new IllegalStateException("Live companion session is already connected");
        }
        listener = liveListener == null ? new EmptyListener() : liveListener;
        sessionId = null;
        client = new NativeWebSocketClient();
        client.connect(URI.create(endpoint), headers(token, deviceId, clientId), new NativeWebSocketClient.Listener() {
            @Override
            public void onOpen() {
                try {
                    client.sendText(LiveCompanionProtocol.helloMessage());
                } catch (IOException ex) {
                    currentListener().onError("实时陪伴 hello 发送失败", ex);
                }
                currentListener().onConnected();
            }

            @Override
            public void onText(String text) {
                handleTextEvent(text);
            }

            @Override
            public void onBinary(byte[] data) {
                currentListener().onAudio(data);
            }

            @Override
            public void onClosed() {
                currentListener().onClosed();
            }

            @Override
            public void onError(Throwable error) {
                currentListener().onError("实时陪伴连接失败", error);
            }
        });
    }

    public void startCall() throws IOException {
        sendText(LiveCompanionProtocol.startCallMessage());
    }

    public void startListening() throws IOException {
        startListening("auto");
    }

    public void startListening(String mode) throws IOException {
        sendText(LiveCompanionProtocol.listenMessage(sessionId, true, mode));
    }

    public void stopListening() throws IOException {
        sendText(LiveCompanionProtocol.listenMessage(sessionId, false, "auto"));
    }

    public void abortCurrentSpeech() throws IOException {
        sendText(LiveCompanionProtocol.abortMessage(sessionId));
    }

    public void setVoiceMuted(boolean muted) throws IOException {
        sendText(LiveCompanionProtocol.voiceMuteMessage(muted));
    }

    public void sendPcmFrame(byte[] pcmFrame) throws IOException {
        NativeWebSocketClient active = requireClient();
        active.sendBinary(pcmFrame);
    }

    public void sendTextInput(String text) throws IOException {
        sendText(LiveCompanionProtocol.inputTextMessage(sessionId, text));
    }

    public String sessionId() {
        return sessionId;
    }

    public void close() {
        NativeWebSocketClient active = client;
        if (active != null) {
            active.close();
        }
    }

    private void sendText(String text) throws IOException {
        requireClient().sendText(text);
    }

    private NativeWebSocketClient requireClient() throws IOException {
        NativeWebSocketClient active = client;
        if (active == null || !active.isOpen()) {
            throw new IOException("实时陪伴还没有连上");
        }
        return active;
    }

    private void handleTextEvent(String text) {
        try {
            JSONObject event = new JSONObject(text);
            String incomingSession = event.optString("session_id", "");
            if (incomingSession.trim().length() > 0) {
                sessionId = incomingSession.trim();
            }
            String type = event.optString("type", "");
            if ("tts".equals(type)) {
                currentListener().onTts(event.optString("state", ""), event.optString("text", ""));
            } else if ("stt".equals(type)) {
                currentListener().onStt(event.optString("text", ""));
            } else if ("emotion".equals(type)) {
                currentListener().onEmotion(
                        event.optString("emotion", ""),
                        (float) event.optDouble("intensity", 0.82d),
                        event.optString("gesture", ""),
                        event.optString("safety_level", ""),
                        event.optString("speech_text", ""),
                        event);
            }
            currentListener().onEvent(type, event);
        } catch (JSONException ex) {
            currentListener().onError("实时陪伴返回内容无法识别", ex);
        }
    }

    private Listener currentListener() {
        Listener active = listener;
        return active == null ? new EmptyListener() : active;
    }

    private static Map<String, String> headers(String token, String deviceId, String clientId) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("device-id", defaultValue(deviceId, "gouxiong-sleep-android"));
        headers.put("client-id", defaultValue(clientId, "gouxiong-sleep"));
        headers.put("protocol-version", "1");
        if (token != null && token.trim().length() > 0) {
            String cleanToken = token.trim();
            headers.put("Authorization", cleanToken.startsWith("Bearer ") ? cleanToken : "Bearer " + cleanToken);
        }
        return headers;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value.trim();
    }

    private static final class EmptyListener implements Listener {
        @Override
        public void onConnected() {
        }

        @Override
        public void onTts(String state, String text) {
        }

        @Override
        public void onStt(String text) {
        }

        @Override
        public void onEmotion(String emotion, float intensity, String gesture, String safetyLevel, String speechText, JSONObject event) {
        }

        @Override
        public void onEvent(String type, JSONObject event) {
        }

        @Override
        public void onAudio(byte[] pcmFrame) {
        }

        @Override
        public void onClosed() {
        }

        @Override
        public void onError(String message, Throwable error) {
        }
    }
}
