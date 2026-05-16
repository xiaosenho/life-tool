package com.lifetool.meals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class MealLog {
    private String id;
    private String userId;
    private String mealType;
    private Instant occurredAt;
    private BigDecimal totalCalories;
    private String note;
    private String mediaAssetId;
    private boolean aiGenerated;
    private Instant createdAt;
    private Instant updatedAt;

    public MealLog() {
        this.id = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
        this.aiGenerated = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMealType() { return mealType; }
    public void setMealType(String mealType) { this.mealType = mealType; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public BigDecimal getTotalCalories() { return totalCalories; }
    public void setTotalCalories(BigDecimal totalCalories) { this.totalCalories = totalCalories; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getMediaAssetId() { return mediaAssetId; }
    public void setMediaAssetId(String mediaAssetId) { this.mediaAssetId = mediaAssetId; }

    public boolean isAiGenerated() { return aiGenerated; }
    public void setAiGenerated(boolean aiGenerated) { this.aiGenerated = aiGenerated; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
