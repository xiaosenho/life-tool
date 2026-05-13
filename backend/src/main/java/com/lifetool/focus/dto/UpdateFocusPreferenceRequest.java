package com.lifetool.focus.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class UpdateFocusPreferenceRequest {

    @Min(value = 1, message = "defaultFocusMinutes must be between 1 and 180")
    @Max(value = 180, message = "defaultFocusMinutes must be between 1 and 180")
    private Integer defaultFocusMinutes;

    @Min(value = 0, message = "shortBreakMinutes must be between 0 and 60")
    @Max(value = 60, message = "shortBreakMinutes must be between 0 and 60")
    private Integer shortBreakMinutes;

    @Min(value = 0, message = "longBreakMinutes must be between 0 and 60")
    @Max(value = 60, message = "longBreakMinutes must be between 0 and 60")
    private Integer longBreakMinutes;

    private Boolean autoStartBreak;

    public Integer getDefaultFocusMinutes() {
        return defaultFocusMinutes;
    }

    public void setDefaultFocusMinutes(Integer defaultFocusMinutes) {
        this.defaultFocusMinutes = defaultFocusMinutes;
    }

    public Integer getShortBreakMinutes() {
        return shortBreakMinutes;
    }

    public void setShortBreakMinutes(Integer shortBreakMinutes) {
        this.shortBreakMinutes = shortBreakMinutes;
    }

    public Integer getLongBreakMinutes() {
        return longBreakMinutes;
    }

    public void setLongBreakMinutes(Integer longBreakMinutes) {
        this.longBreakMinutes = longBreakMinutes;
    }

    public Boolean getAutoStartBreak() {
        return autoStartBreak;
    }

    public void setAutoStartBreak(Boolean autoStartBreak) {
        this.autoStartBreak = autoStartBreak;
    }
}
