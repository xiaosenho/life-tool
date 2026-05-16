package com.lifetool.habits;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class HabitCheckin {

    private String id;
    private String userId;
    private String habitId;
    private LocalDate checkinDate;
    private int count;
    private String note;
    private Instant createdAt;
    private Instant updatedAt;

    public HabitCheckin() {
        this.id = UUID.randomUUID().toString();
        this.count = 1;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getHabitId() { return habitId; }
    public void setHabitId(String habitId) { this.habitId = habitId; }

    public LocalDate getCheckinDate() { return checkinDate; }
    public void setCheckinDate(LocalDate checkinDate) { this.checkinDate = checkinDate; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
