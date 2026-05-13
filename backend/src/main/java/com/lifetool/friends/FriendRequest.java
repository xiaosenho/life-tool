package com.lifetool.friends;

import java.time.Instant;
import java.util.UUID;

public class FriendRequest {

    public enum Status { PENDING, ACCEPTED, REJECTED }

    private String id;
    private String fromUserId;
    private String toUserId;
    private Status status;
    private Instant createdAt;
    private Instant updatedAt;

    public FriendRequest(String fromUserId, String toUserId) {
        this.id = UUID.randomUUID().toString();
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getFromUserId() { return fromUserId; }
    public String getToUserId() { return toUserId; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
