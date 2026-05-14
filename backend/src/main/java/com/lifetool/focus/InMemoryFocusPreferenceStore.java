package com.lifetool.focus;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryFocusPreferenceStore implements FocusPreferenceStore {

    private final Map<String, FocusPreference> preferencesByUserId = new ConcurrentHashMap<>();

    @Override
    public FocusPreference save(FocusPreference preference) {
        preferencesByUserId.put(preference.getUserId(), preference);
        return preference;
    }

    @Override
    public Optional<FocusPreference> findByUserId(String userId) {
        return Optional.ofNullable(preferencesByUserId.get(userId));
    }
}
