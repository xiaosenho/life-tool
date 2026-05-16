package com.lifetool.habits;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HabitCheckinStore {
    HabitCheckin save(HabitCheckin checkin);

    Optional<HabitCheckin> findByHabitIdAndDate(String habitId, LocalDate date);

    void deleteByHabitIdAndDate(String habitId, LocalDate date);

    List<HabitCheckin> findByHabitId(String habitId);

    List<HabitCheckin> findByUserIdAndDate(String userId, LocalDate date);

    List<HabitCheckin> findByUserIdAndDateRange(String userId, LocalDate from, LocalDate to);
}
