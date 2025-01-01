package com.sohocn.DeepSeek.settings;

import com.intellij.util.messages.Topic;

public interface ApiKeyChangeNotifier {
    Topic<ApiKeyChangeNotifier> TOPIC = Topic.create("DeepSeek API Key Changed", ApiKeyChangeNotifier.class);
    
    void apiKeyChanged(ApiKeyChangeEvent event);
} 