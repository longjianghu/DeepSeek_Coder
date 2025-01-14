package com.sohocn.deep.seek.window;

import java.util.Objects;

public class ChatMessage {
    // 聊天消息数据类
    private final String content;
    private final String role;

    public ChatMessage(String content, boolean user) {
        this.content = content;
        this.role = user ? "user" : "assistant";
    }

    public String getRole() {
        return this.role;
    }

    public String getContent() {
        return content;
    }

    public boolean isUser() {
        return Objects.equals(this.role, "user");
    }
}
