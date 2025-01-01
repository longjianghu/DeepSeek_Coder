package com.sohocn.DeepSeek.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.ide.util.PropertiesComponent;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeepSeekService {
    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final String API_KEY = "com.sohocn.deepseek.apiKey";

    public String sendMessage(String message) throws IOException {
        String apiKey = PropertiesComponent.getInstance().getValue(API_KEY);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("API Key not configured");
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(API_URL);
            
            // 设置请求头
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + apiKey);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("stream", false);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", "You are a helpful assistant."
            ));
            messages.add(Map.of(
                "role", "user",
                "content", message
            ));
            requestBody.put("messages", messages);

            // 转换为JSON
            String jsonBody = new Gson().toJson(requestBody);
            httpPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

            // 发送请求
            try (CloseableHttpResponse response = client.execute(httpPost)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    String jsonResponse = EntityUtils.toString(entity);
                    JsonObject jsonObject = new Gson().fromJson(jsonResponse, JsonObject.class);
                    return jsonObject.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();
                }
            }
        }
        return "Error: No response from API";
    }
} 