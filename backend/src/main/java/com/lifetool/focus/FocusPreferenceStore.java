package com.lifetool.focus;

import java.util.Optional;

public interface FocusPreferenceStore {
    FocusPreference save(FocusPreference preference);

    Optional<FocusPreference> findByUserId(String userId);
}
