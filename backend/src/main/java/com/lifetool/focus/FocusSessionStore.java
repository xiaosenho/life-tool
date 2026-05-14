package com.lifetool.focus;

import java.util.List;
import java.util.Optional;

public interface FocusSessionStore {
    FocusSession save(FocusSession session);

    Optional<FocusSession> findById(String id);

    List<FocusSession> findByUserId(String userId);

    List<FocusSession> findByUserIdAndMonth(String userId, String month);
}
