package com.sohocn.deep.seek.coder.event;

public class ChangeEvent {
    private final String apiKey;

    public ChangeEvent(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }
} 