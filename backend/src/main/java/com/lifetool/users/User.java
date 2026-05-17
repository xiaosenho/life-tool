package com.lifetool.users;

import java.time.Instant;
import java.util.UUID;

public class User {

    private String id;
    private String email;
    private String passwordHash;
    private String displayName;
    private String avatarAssetId;
    private Instant createdAt;

    public User(String email, String passwordHash, String displayName) {
        this.id = UUID.randomUUID().toString();
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.createdAt = Instant.now();
    }

    public User(String id, String email, String passwordHash, String displayName, String avatarAssetId, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.avatarAssetId = avatarAssetId;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getAvatarAssetId() { return avatarAssetId; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setAvatarAssetId(String avatarAssetId) { this.avatarAssetId = avatarAssetId; }
}
