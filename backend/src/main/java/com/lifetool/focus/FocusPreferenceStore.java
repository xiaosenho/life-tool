package com.lifetool.focus;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class FocusPreferenceStore {

    private final Map<String, FocusPreference> preferencesByUserId = new ConcurrentHashMap<>();

    public FocusPreference save(FocusPreference preference) {
        preferencesByUserId.put(preference.getUserId(), preference);
        return preference;
    }

    public Optional<FocusPreference> findByUserId(String userId) {
        return Optional.ofNullable(preferencesByUserId.get(userId));
    }
}
