package com.lifetool.ai;

import java.time.Instant;
import java.util.UUID;

public class AiMemoryItem {
    private final String id = UUID.randomUUID().toString();
    private final String userId;
    private final String type;
    private final String content;
    private final String source;
    private final Instant createdAt = Instant.now();
    private boolean enabled = true;
    private Instant deletedAt;

    public AiMemoryItem(String userId, String type, String content, String source) {
        this.userId = userId;
        this.type = type;
        this.content = content;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void disable() {
        this.enabled = false;
        this.deletedAt = Instant.now();
    }
}
