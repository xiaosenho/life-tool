package com.lifetool.focus;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.lifetool.focus.dto.FocusPreferenceResponse;
import com.lifetool.focus.dto.UpdateFocusPreferenceRequest;

@Service
public class FocusPreferenceService {

    private final FocusPreferenceStore store;

    public FocusPreferenceService(FocusPreferenceStore store) {
        this.store = store;
    }

    public FocusPreferenceResponse getPreference(String userId) {
        FocusPreference pref = store.findByUserId(userId)
                .orElseGet(() -> new FocusPreference(userId));
        return FocusPreferenceResponse.from(pref);
    }

    public FocusPreferenceResponse updatePreference(String userId, UpdateFocusPreferenceRequest request) {
        FocusPreference pref = store.findByUserId(userId)
                .orElseGet(() -> new FocusPreference(userId));

        if (request.getDefaultFocusMinutes() != null) {
            pref.setDefaultFocusMinutes(request.getDefaultFocusMinutes());
        }
        if (request.getShortBreakMinutes() != null) {
            pref.setShortBreakMinutes(request.getShortBreakMinutes());
        }
        if (request.getLongBreakMinutes() != null) {
            pref.setLongBreakMinutes(request.getLongBreakMinutes());
        }
        if (request.getAutoStartBreak() != null) {
            pref.setAutoStartBreak(request.getAutoStartBreak());
        }

        pref.setUpdatedAt(Instant.now());
        store.save(pref);

        return FocusPreferenceResponse.from(pref);
    }
}
