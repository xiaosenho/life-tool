package com.lifetool.habits;

import java.time.Instant;
import java.util.UUID;

public class Habit {

    private String id;
    private String userId;
    private String name;
    private String description;
    private String frequencyType;
    private int[] frequencyDays;
    private int targetCount;
    private String color;
    private String icon;
    private boolean archived;
    private Instant createdAt;
    private Instant updatedAt;

    public Habit() {
        this.id = UUID.randomUUID().toString();
        this.frequencyType = "daily";
        this.frequencyDays = new int[0];
        this.targetCount = 1;
        this.archived = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getFrequencyType() { return frequencyType; }
    public void setFrequencyType(String frequencyType) { this.frequencyType = frequencyType; }

    public int[] getFrequencyDays() { return frequencyDays; }
    public void setFrequencyDays(int[] frequencyDays) { this.frequencyDays = frequencyDays; }

    public int getTargetCount() { return targetCount; }
    public void setTargetCount(int targetCount) { this.targetCount = targetCount; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
