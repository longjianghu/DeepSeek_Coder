package com.sohocn.deep.seek.settings;

public class ApiKeyChangeEvent {
    private final String apiKey;
    private final int historyLimit;

    public ApiKeyChangeEvent(String apiKey, int historyLimit) {
        this.apiKey = apiKey;
        this.historyLimit = historyLimit;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getHistoryLimit() {
        return historyLimit;
    }
} 