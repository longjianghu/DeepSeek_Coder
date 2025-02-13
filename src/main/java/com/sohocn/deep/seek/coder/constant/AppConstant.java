package com.sohocn.deep.seek.coder.constant;

public interface AppConstant {
    String API_KEY = "com.sohocn.deepseek.apiKey";
    String CHAT_HISTORY = "com.sohocn.deepseek.chatHistory";
    String HISTORY_LIMIT = "com.sohocn.deepseek.historyLimit";
    String OPTION_VALUE = "com.sohocn.deepseek.optionValue";
    String API_URL = "https://api.deepseek.com/v1/chat/completions";
    String APPLY_URL = "https://www.deepseek.com?from=DeepSeekCoder";
    String PROMPT = "com.sohocn.deepseek.prompt";
    String MODEL = "com.sohocn.deepseek.model";
    String CONTEXT = "com.sohocn.deepseek.context";
    String PLUGIN_ID = "com.sohocn.deep.seek.coder.settings.DeepSeekSettingsConfigurable";
    String DEFAULT_MODEL = "deepseek-chat";
    String DEFAULT_PROMPT =
        "You are a helpful assistant specialized in programming and software development.Your task is to assist users with questions related to coding, debugging, software design, algorithms, and other programming-related topics. If a user asks a question outside of these areas, politely inform them that you are only able to assist with programming-related queries.";
    String NO_API_KEY_PROMPT = "Please configure your DeepSeek API key in Settings -> Tools -> DeepSeek Coder!";
}
