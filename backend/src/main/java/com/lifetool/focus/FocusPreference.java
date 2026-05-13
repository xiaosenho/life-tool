package com.lifetool.focus;

import java.time.Instant;

public class FocusPreference {

    private String userId;
    private int defaultFocusMinutes;
    private int shortBreakMinutes;
    private int longBreakMinutes;
    private boolean autoStartBreak;
    private Instant updatedAt;

    public FocusPreference() {
    }

    public FocusPreference(String userId) {
        this.userId = userId;
        this.defaultFocusMinutes = 25;
        this.shortBreakMinutes = 5;
        this.longBreakMinutes = 15;
        this.autoStartBreak = false;
        this.updatedAt = Instant.now();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getDefaultFocusMinutes() {
        return defaultFocusMinutes;
    }

    public void setDefaultFocusMinutes(int defaultFocusMinutes) {
        this.defaultFocusMinutes = defaultFocusMinutes;
    }

    public int getShortBreakMinutes() {
        return shortBreakMinutes;
    }

    public void setShortBreakMinutes(int shortBreakMinutes) {
        this.shortBreakMinutes = shortBreakMinutes;
    }

    public int getLongBreakMinutes() {
        return longBreakMinutes;
    }

    public void setLongBreakMinutes(int longBreakMinutes) {
        this.longBreakMinutes = longBreakMinutes;
    }

    public boolean isAutoStartBreak() {
        return autoStartBreak;
    }

    public void setAutoStartBreak(boolean autoStartBreak) {
        this.autoStartBreak = autoStartBreak;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
