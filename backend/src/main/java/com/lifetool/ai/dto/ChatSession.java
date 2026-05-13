package com.lifetool.ai.dto;

import java.time.Instant;
import java.util.UUID;

public class ChatSession {
    private String id;
    private String userId;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;

    public ChatSession(String userId, String title) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.title = title;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setTitle(String title) { this.title = title; this.updatedAt = Instant.now(); }
}
