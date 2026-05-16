package com.lifetool.habits;

import java.util.List;
import java.util.Optional;

public interface HabitStore {
    Habit save(Habit habit);

    Optional<Habit> findById(String id);

    List<Habit> findByUserId(String userId);

    void deleteById(String id);
}
