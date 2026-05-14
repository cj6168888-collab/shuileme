package com.gouxiong.sleep.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public final class DeepSeekClient {
    private static final String ENDPOINT = "https://api.deepseek.com/chat/completions";

    private DeepSeekClient() {
    }

    public static String chat(String apiKey, String model, String systemPrompt, String userPrompt) throws Exception {
        if (apiKey == null || !apiKey.startsWith("sk-")) {
            throw new IllegalArgumentException("DeepSeek API Key 未配置");
        }
        return chatWithUserContent(apiKey, model, systemPrompt, userPrompt);
    }

    public static String chatWithImage(String apiKey, String model, String systemPrompt, String userPrompt, String jpegBase64) throws Exception {
        if (apiKey == null || !apiKey.startsWith("sk-")) {
            throw new IllegalArgumentException("DeepSeek API Key 未配置");
        }
        if (jpegBase64 == null || jpegBase64.length() == 0) {
            throw new IllegalArgumentException("图片为空");
        }
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", userPrompt));
        content.put(new JSONObject()
                .put("type", "image_url")
                .put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + jpegBase64)));
        return chatWithUserContent(apiKey, model, systemPrompt, content);
    }

    private static String chatWithUserContent(String apiKey, String model, String systemPrompt, Object userContent) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(45000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);

        JSONObject body = new JSONObject();
        body.put("model", model == null || model.length() == 0 ? "deepseek-v4-flash" : model);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        messages.put(new JSONObject().put("role", "user").put("content", userContent));
        body.put("messages", messages);
        body.put("stream", false);
        body.put("max_tokens", 650);
        body.put("temperature", 0.7);
        body.put("thinking", new JSONObject().put("type", "disabled"));

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
        writer.write(body.toString());
        writer.flush();
        writer.close();

        int code = connection.getResponseCode();
        String raw = readAll(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("DeepSeek 请求失败：" + code + " " + limit(raw));
        }

        JSONObject response = new JSONObject(raw);
        JSONArray choices = response.getJSONArray("choices");
        if (choices.length() == 0) {
            throw new IllegalStateException("DeepSeek 没有返回回答");
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        String content = message.optString("content", "").trim();
        if (content.length() == 0) {
            throw new IllegalStateException("DeepSeek 返回为空");
        }
        return content;
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

    private static String limit(String value) {
        if (value == null) return "";
        return value.length() > 240 ? value.substring(0, 240) : value;
    }
}
