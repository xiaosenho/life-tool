package com.lifetool.habits;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryHabitCheckinStore implements HabitCheckinStore {

    private final Map<String, HabitCheckin> byId = new ConcurrentHashMap<>();

    @Override
    public HabitCheckin save(HabitCheckin checkin) {
        byId.put(checkin.getId(), checkin);
        return checkin;
    }

    @Override
    public Optional<HabitCheckin> findByHabitIdAndDate(String habitId, LocalDate date) {
        return byId.values().stream()
                .filter(c -> habitId.equals(c.getHabitId()) && date.equals(c.getCheckinDate()))
                .findFirst();
    }

    @Override
    public List<HabitCheckin> findByHabitId(String habitId) {
        return byId.values().stream()
                .filter(c -> habitId.equals(c.getHabitId()))
                .toList();
    }

    @Override
    public List<HabitCheckin> findByUserIdAndDate(String userId, LocalDate date) {
        return byId.values().stream()
                .filter(c -> userId.equals(c.getUserId()) && date.equals(c.getCheckinDate()))
                .toList();
    }
}
