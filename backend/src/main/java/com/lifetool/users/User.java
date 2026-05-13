package com.lifetool.users;

import java.time.Instant;
import java.util.UUID;

public class User {

    private String id;
    private String email;
    private String passwordHash;
    private String displayName;
    private Instant createdAt;

    public User(String email, String passwordHash, String displayName) {
        this.id = UUID.randomUUID().toString();
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public Instant getCreatedAt() { return createdAt; }
}
