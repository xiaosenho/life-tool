package com.lifetool.focus;

import java.time.Instant;
import java.util.UUID;

public class FocusSession {

    private String id;
    private String userId;
    private String mode;
    private int targetSeconds;
    private int actualSeconds;
    private String status;
    private Instant startedAt;
    private Instant endedAt;
    private String note;
    private Instant createdAt;
    private Instant updatedAt;

    public FocusSession() {
        this.id = UUID.randomUUID().toString();
        this.mode = "pomodoro";
        this.targetSeconds = 1500;
        this.actualSeconds = 0;
        this.status = "running";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public int getTargetSeconds() { return targetSeconds; }
    public void setTargetSeconds(int targetSeconds) { this.targetSeconds = targetSeconds; }

    public int getActualSeconds() { return actualSeconds; }
    public void setActualSeconds(int actualSeconds) { this.actualSeconds = actualSeconds; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
