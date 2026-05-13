package com.lifetool.ai.dto;

import java.time.Instant;
import java.util.UUID;

public class ChatMessage {
    private String id;
    private String sessionId;
    private String role;
    private String content;
    private Instant createdAt;

    public ChatMessage(String sessionId, String role, String content) {
        this.id = UUID.randomUUID().toString();
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
