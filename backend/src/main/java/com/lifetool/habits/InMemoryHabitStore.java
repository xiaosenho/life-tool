package com.lifetool.habits;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryHabitStore implements HabitStore {

    private final Map<String, Habit> byId = new ConcurrentHashMap<>();

    @Override
    public Habit save(Habit habit) {
        byId.put(habit.getId(), habit);
        return habit;
    }

    @Override
    public Optional<Habit> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<Habit> findByUserId(String userId) {
        return byId.values().stream()
                .filter(h -> userId.equals(h.getUserId()) && !h.isArchived())
                .toList();
    }

    @Override
    public void deleteById(String id) {
        Habit habit = byId.get(id);
        if (habit != null) {
            habit.setArchived(true);
        }
    }
}
