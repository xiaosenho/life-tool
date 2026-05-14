package com.lifetool.ai;

import java.time.Instant;
import java.util.UUID;

public class AiChatMessage {
    private final String id = UUID.randomUUID().toString();
    private final String sessionId;
    private final String userId;
    private final String role;
    private final String content;
    private final int seq;
    private final Instant createdAt = Instant.now();

    public AiChatMessage(String sessionId, String userId, String role, String content, int seq) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.seq = seq;
    }

    public String getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public int getSeq() {
        return seq;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
