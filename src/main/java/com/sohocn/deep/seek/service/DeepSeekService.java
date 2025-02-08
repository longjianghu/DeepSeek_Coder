package com.sohocn.deep.seek.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.intellij.ide.util.PropertiesComponent;
import com.sohocn.deep.seek.constant.AppConstant;
import com.sohocn.deep.seek.window.ChatMessage;

public class DeepSeekService {
    // 修改方法签名，添加 token 使用回调
    public void streamMessage(String message, Consumer<String> onChunk, Runnable onComplete) throws IOException {
        PropertiesComponent instance = PropertiesComponent.getInstance();

        String apiKey = instance.getValue(AppConstant.API_KEY);
        String prompt = instance.getValue(AppConstant.PROMPT);
        String model = instance.getValue(AppConstant.MODEL);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("API Key not configured");
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(AppConstant.API_URL);

            // 设置请求头
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + apiKey);
            httpPost.setHeader("Accept", "text/event-stream");

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.0);
            requestBody.put("stream", true);

            List<Map<String, String>> messages = new ArrayList<>();

            if (prompt != null && !prompt.trim().isEmpty()) {
                messages.add(Map.of("role", "system", "content", prompt));
            }

            // 获取历史记录
            String chatHistoryJson = instance.getValue(AppConstant.CHAT_HISTORY);
            String optionValue = instance.getValue(AppConstant.OPTION_VALUE);

            if (chatHistoryJson != null && !chatHistoryJson.isEmpty()) {
                Type listType = new TypeToken<List<ChatMessage>>() {}.getType();
                List<ChatMessage> chatMessages = new Gson().fromJson(chatHistoryJson, listType);

                int limitNumber = Objects.nonNull(optionValue) ? Integer.parseInt(optionValue) : 0;

                // 获取最近的一条交互记录
                if (!chatMessages.isEmpty()) {
                    int size = chatMessages.size();
                    int limit = size - limitNumber * 2;

                    List<ChatMessage> lastTwoMessages = limit > 0 ? chatMessages.subList(limit, size) : chatMessages;

                    for (ChatMessage lastTwoMessage : lastTwoMessages) {
                        messages.add(Map.of("role", lastTwoMessage.getRole(), "content", lastTwoMessage.getContent()));
                    }
                }
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

                                // 检查是否有 reasoning_content 字段
                                if (delta.has("reasoning_content")) {
                                    String content = delta.get("reasoning_content").getAsString();
                                    onChunk.accept(content);
                                }

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