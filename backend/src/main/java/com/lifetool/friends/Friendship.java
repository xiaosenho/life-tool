package com.lifetool.friends;

import java.time.Instant;
import java.util.UUID;

public class Friendship {

    private String id;
    private String userId;
    private String friendUserId;
    private Instant createdAt;

    public Friendship(String userId, String friendUserId) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.friendUserId = friendUserId;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getFriendUserId() { return friendUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
