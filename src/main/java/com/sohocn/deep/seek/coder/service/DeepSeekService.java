package com.sohocn.deep.seek.coder.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.sohocn.deep.seek.coder.bo.MessageBO;
import com.sohocn.deep.seek.coder.config.PlatformConfig;
import com.sohocn.deep.seek.coder.constant.AppConstant;
import com.sohocn.deep.seek.coder.sidebar.ChatMessage;

public class DeepSeekService {
    private static final Logger logger = Logger.getInstance(DeepSeekService.class);
    private final Gson gson = new Gson();
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private ClassicHttpResponse currentResponse;
    private final Object responseLock = new Object();

    public void cancelRequest() {
        isCancelled.set(true);

        synchronized (responseLock) {
            if (currentResponse != null) {
                try {
                    HttpEntity entity = currentResponse.getEntity();

                    if (entity != null) {
                        try {
                            entity.close();
                        } catch (IOException e) {
                            // 忽略实体关闭错误，因为这可能已经被关闭
                            logger.debug("Entity already closed: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    // 忽略获取实体时的错误，因为响应可能已经被关闭
                    logger.debug("Response already closed: " + e.getMessage());
                }

                currentResponse = null;
            }
        }
    }

    public void streamMessage(String message, Consumer<String> onChunk, Runnable onComplete) throws IOException {
        isCancelled.set(false);
        PropertiesComponent instance = PropertiesComponent.getInstance();

        String platform = instance.getValue(AppConstant.PLATFORM);
        String apiKey = instance.getValue(AppConstant.API_KEY);
        String prompt = instance.getValue(AppConstant.PROMPT);
        String model = instance.getValue(AppConstant.MODEL);

        // 验证必要的配置
        if (platform == null || platform.trim().isBlank()) {
            throw new IllegalStateException("Platform not configured");
        }

        if (apiKey == null || apiKey.trim().isBlank()) {
            throw new IllegalStateException("API Key not configured");
        }

        if (model == null || model.trim().isBlank()) {
            throw new IllegalStateException("Model not configured");
        }

        PlatformConfig platformConfig = new PlatformConfig();
        model = Objects.equals(platform, AppConstant.SILICON_FLOW) ? platformConfig.siliconFlowModelMap(model) : model;

        String apiUrl = platformConfig.apiUrlMap(platform);

        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            throw new IllegalStateException("Invalid API URL for platform: " + platform);
        }

        RequestConfig requestConfig = RequestConfig
            .custom()
            .setConnectionKeepAlive(Timeout.ofMilliseconds(5000))
            .setConnectionRequestTimeout(Timeout.ofMilliseconds(5000))
            .setResponseTimeout(Timeout.ofMilliseconds(5000))
            .build();

        try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(requestConfig).build()) {
            HttpPost httpPost = new HttpPost(apiUrl);

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
                List<ChatMessage> chatMessages = gson.fromJson(chatHistoryJson, listType);

                if (chatMessages != null && !chatMessages.isEmpty()) {
                    int limitNumber = Objects.nonNull(optionValue) ? Integer.parseInt(optionValue) : 0;
                    int size = chatMessages.size();
                    int limit = size - limitNumber * 2;

                    List<ChatMessage> lastTwoMessages = limit > 0 ? chatMessages.subList(limit, size) : chatMessages;

                    for (ChatMessage lastTwoMessage : lastTwoMessages) {
                        if (lastTwoMessage != null && lastTwoMessage.getRole() != null
                            && lastTwoMessage.getContent() != null) {
                            messages
                                .add(Map.of("role", lastTwoMessage.getRole(), "content", lastTwoMessage.getContent()));
                        }
                    }
                }
            }

            messages.add(Map.of("role", "user", "content", message));
            requestBody.put("messages", messages);

            // 转换为JSON
            String jsonBody = gson.toJson(requestBody);
            if (jsonBody == null) {
                throw new IllegalStateException("Failed to serialize request body");
            }

            httpPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

            // 创建响应处理器
            HttpClientResponseHandler<Void> responseHandler = response -> {
                if (response == null) {
                    throw new IOException("Null response received");
                }

                synchronized (responseLock) {
                    currentResponse = response;
                }

                int statusCode = response.getCode();

                if (statusCode != 200) {
                    throw new IOException("API request failed with status code: " + statusCode);
                }

                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new IOException("Empty response from API");
                }

                try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8))) {
                    String line;

                    while ((line = reader.readLine()) != null && !isCancelled.get()) {
                        if (line.isEmpty()) {
                            continue;
                        }

                        if (line.startsWith("data: ")) {
                            String jsonData = line.substring(6);
                            if (jsonData.isEmpty()) {
                                continue;
                            }

                            if ("[DONE]".equals(jsonData)) {
                                break;
                            }

                            try {
                                MessageBO messageBO = gson.fromJson(jsonData, MessageBO.class);
                                if (messageBO == null) {
                                    continue;
                                }

                                MessageBO.Choices choices = Optional
                                    .ofNullable(messageBO.getChoices())
                                    .filter(list -> !list.isEmpty())
                                    .map(list -> list.get(0))
                                    .orElse(null);

                                if (choices == null) {
                                    continue;
                                }

                                MessageBO.Delta delta = choices.getDelta();
                                if (delta == null) {
                                    continue;
                                }

                                String content =
                                    Optional.ofNullable(delta.getReasoningContent()).orElseGet(delta::getContent);

                                if (content != null && !content.isEmpty()) {
                                    onChunk.accept(content);
                                }
                            } catch (Exception e) {
                                logger.error("Error processing response chunk: " + e.getMessage(), e);
                            }
                        }
                    }
                }
                return null;
            };

            try {
                client.execute(httpPost, responseHandler);
            } catch (Exception e) {
                logger.error("Error during API request: " + e.getMessage(), e);
                throw e;
            } finally {
                if (currentResponse != null) {
                    try {
                        currentResponse.close();
                    } catch (IOException e) {
                        logger.error("Error closing response: " + e.getMessage(), e);
                    }
                    currentResponse = null;
                }
            }
        } catch (Exception e) {
            logger.error("Error creating HTTP client: " + e.getMessage(), e);
            throw e;
        }

        if (!isCancelled.get()) {
            onComplete.run();
        }
    }
}