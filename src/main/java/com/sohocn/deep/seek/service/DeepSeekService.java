package com.sohocn.deep.seek.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.ide.util.PropertiesComponent;

public class DeepSeekService {
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String API_KEY = "com.sohocn.deepseek.apiKey";
    private static final String PROMPT = "com.sohocn.deepseek.prompt";

    // 修改方法签名，添加 token 使用回调
    public void streamMessage(String message, Consumer<String> onChunk, Runnable onComplete) throws IOException {
        String apiKey = PropertiesComponent.getInstance().getValue(API_KEY);
        String prompt = PropertiesComponent.getInstance().getValue(PROMPT);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("API Key not configured");
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(API_URL);

            // 设置请求头
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + apiKey);
            httpPost.setHeader("Accept", "text/event-stream");

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("stream", true); // 启用流式输出

            List<Map<String, String>> messages = new ArrayList<>();

            if (prompt != null && !prompt.trim().isEmpty()) {
                messages.add(Map.of("role", "system", "content", prompt));
            }

            messages.add(Map.of("role", "user", "content", message));
            requestBody.put("messages", messages);

            // 转换为JSON
            String jsonBody = new com.google.gson.Gson().toJson(requestBody);
            httpPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

            // 发送请求并处理流式响应
            try (CloseableHttpResponse response = client.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    // 处理非 200 响应
                    throw new IOException("API request failed with status code: " + statusCode);
                }

                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new IOException("Empty response from API");
                }

                try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8))) {
                    String line;

                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty()) {
                            continue;
                        }

                        if (line.startsWith("data: ")) {
                            String jsonData = line.substring(6);
                            if ("[DONE]".equals(jsonData)) {
                                break;
                            }

                            try {
                                JsonObject jsonObject = JsonParser.parseString(jsonData).getAsJsonObject();

                                JsonObject delta = jsonObject
                                    .getAsJsonArray("choices")
                                    .get(0)
                                    .getAsJsonObject()
                                    .getAsJsonObject("delta");

                                // 检查是否有 content 字段
                                if (delta.has("content")) {
                                    String content = delta.get("content").getAsString();
                                    // 直接传递原始内容，由显示层处理渲染
                                    onChunk.accept(content);
                                }
                            } catch (Exception e) {
                                // 忽略解析错误，继续处理下一块
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error during API request: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        } catch (Exception e) {
            System.err.println("Error creating HTTP client: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
        onComplete.run();
    }
}