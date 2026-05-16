package com.lifetool.ai;

import java.time.Instant;
import java.util.UUID;

public class AiChatSession {
    private final String id = UUID.randomUUID().toString();
    private final String userId;
    private final String title;
    private final boolean useLongTermMemory;
    private final Instant createdAt = Instant.now();
    private Instant updatedAt = createdAt;
    private boolean deleted;

    public AiChatSession(String userId, String title, boolean useLongTermMemory) {
        this.userId = userId;
        this.title = title;
        this.useLongTermMemory = useLongTermMemory;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public boolean isUseLongTermMemory() {
        return useLongTermMemory;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
        touch();
    }
}
