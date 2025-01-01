package com.sohocn.DeepSeek.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.ide.util.PropertiesComponent;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DeepSeekService {
    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final String API_KEY = "com.sohocn.deepseek.apiKey";

    // 创建一个 TokenUsage 类来存储 token 信息
    public static class TokenUsage {
        public final int promptTokens;
        public final int completionTokens;
        public final int totalTokens;

        public TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }
    }

    // 修改方法签名，添加 token 使用回调
    public void streamMessage(String message, Consumer<String> onChunk, Consumer<TokenUsage> onTokenUsage, Runnable onComplete) throws IOException {
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
            requestBody.put("stream", true); // 启用流式输出

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                    "role", "system",
                    "content",
                    "You are a helpful assistant specialized in programming and software development. Your task is to assist users with questions related to coding, debugging, software design, algorithms, and other programming-related topics. If a user asks a question outside of these areas, politely inform them that you are only able to assist with programming-related queries."));
            messages.add(Map.of(
                    "role", "user",
                    "content", message));
            requestBody.put("messages", messages);

            // 转换为JSON
            String jsonBody = new com.google.gson.Gson().toJson(requestBody);
            httpPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

            // 发送请求并处理流式响应
            try (CloseableHttpResponse response = client.execute(httpPost)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8))) {
                        String line;

                        while ((line = reader.readLine()) != null) {
                            if (line.isEmpty())
                                continue;
                            if (line.startsWith("data: ")) {
                                String jsonData = line.substring(6);
                                if ("[DONE]".equals(jsonData)) {
                                    break;
                                }

                                try {
                                    JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();
                                    
                                    // 检查是否有 usage 信息
                                    if (jsonObject.has("usage")) {
                                        JsonObject usage = jsonObject.getAsJsonObject("usage");
                                        TokenUsage tokenUsage = new TokenUsage(
                                            usage.get("prompt_tokens").getAsInt(),
                                            usage.get("completion_tokens").getAsInt(),
                                            usage.get("total_tokens").getAsInt()
                                        );
                                        onTokenUsage.accept(tokenUsage);
                                    }

                                    JsonObject delta = jsonObject.getAsJsonArray("choices")
                                            .get(0).getAsJsonObject()
                                            .getAsJsonObject("delta");

                                    // 检查是否有 content 字段
                                    if (delta.has("content")) {
                                        String content = delta.get("content").getAsString();
                                        onChunk.accept(content);
                                    }
                                } catch (Exception e) {
                                    // 忽略解析错误，继续处理下一块
                                }
                            }
                        }
                    }
                }
            }
        }
        onComplete.run();
    }
}