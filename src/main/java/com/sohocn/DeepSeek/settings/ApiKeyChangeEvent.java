package com.sohocn.DeepSeek.settings;

public class ApiKeyChangeEvent {
    private final String apiKey;

    public ApiKeyChangeEvent(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }
} 