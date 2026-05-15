package com.lifetool.friends;

import java.time.Instant;
import java.util.UUID;

public class FriendMessage {

    public enum MessageType {
        TEXT,
        CHEER
    }

    private final String id;
    private final String fromUserId;
    private final String toUserId;
    private final MessageType type;
    private final String content;
    private final Instant createdAt;
    private Instant readAt;

    public FriendMessage(String fromUserId, String toUserId, MessageType type, String content) {
        this(UUID.randomUUID().toString(), fromUserId, toUserId, type, content, Instant.now(), null);
    }

    public FriendMessage(
            String id,
            String fromUserId,
            String toUserId,
            MessageType type,
            String content,
            Instant createdAt,
            Instant readAt) {
        this.id = id;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.type = type;
        this.content = content;
        this.createdAt = createdAt;
        this.readAt = readAt;
    }

    public String getId() {
        return id;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public String getToUserId() {
        return toUserId;
    }

    public MessageType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead(Instant readAt) {
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }
}
