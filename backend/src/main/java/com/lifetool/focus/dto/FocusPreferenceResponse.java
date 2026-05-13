package com.lifetool.focus.dto;

import java.time.Instant;

import com.lifetool.focus.FocusPreference;

public record FocusPreferenceResponse(
        int defaultFocusMinutes,
        int shortBreakMinutes,
        int longBreakMinutes,
        boolean autoStartBreak,
        Instant updatedAt) {

    public static FocusPreferenceResponse from(FocusPreference pref) {
        return new FocusPreferenceResponse(
                pref.getDefaultFocusMinutes(),
                pref.getShortBreakMinutes(),
                pref.getLongBreakMinutes(),
                pref.isAutoStartBreak(),
                pref.getUpdatedAt());
    }
}
