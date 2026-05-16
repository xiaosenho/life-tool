package com.lifetool.habits;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lifetool.common.TimeSupport;
import com.lifetool.habits.dto.CreateCheckinRequest;
import com.lifetool.habits.dto.CreateHabitRequest;
import com.lifetool.habits.dto.HabitCheckinResponse;
import com.lifetool.habits.dto.HabitResponse;
import com.lifetool.habits.dto.UpdateHabitRequest;
import com.lifetool.leaderboards.LeaderboardStatsStore;

@Service
public class HabitService {

    private static final List<String> VALID_FREQUENCY_TYPES = List.of("daily", "weekly", "custom");

    private final HabitStore habitStore;
    private final HabitCheckinStore checkinStore;
    private final LeaderboardStatsStore statsStore;

    public HabitService(HabitStore habitStore, HabitCheckinStore checkinStore, LeaderboardStatsStore statsStore) {
        this.habitStore = habitStore;
        this.checkinStore = checkinStore;
        this.statsStore = statsStore;
    }

    public HabitResponse createHabit(String userId, CreateHabitRequest request) {
        String frequencyType = request.frequencyType() == null || request.frequencyType().isBlank()
                ? "daily" : request.frequencyType();
        if (!VALID_FREQUENCY_TYPES.contains(frequencyType)) {
            throw new HabitException("VALIDATION_ERROR", "Invalid frequencyType: " + frequencyType);
        }

        Habit habit = new Habit();
        habit.setUserId(userId);
        habit.setName(request.name().trim());
        habit.setDescription(blankToNull(request.description()));
        habit.setFrequencyType(frequencyType);
        habit.setFrequencyDays(request.frequencyDays() != null ? request.frequencyDays() : new int[0]);
        habit.setTargetCount(Math.max(1, request.targetCount()));
        habit.setColor(blankToNull(request.color()));
        habit.setIcon(blankToNull(request.icon()));

        habitStore.save(habit);
        refreshTodayStats(userId);
        return HabitResponse.from(habit);
    }

    public List<HabitResponse> listHabits(String userId) {
        return habitStore.findByUserId(userId).stream()
                .map(HabitResponse::from)
                .toList();
    }

    public HabitResponse updateHabit(String userId, String id, UpdateHabitRequest request) {
        Habit habit = findOwnedHabit(userId, id);

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new HabitException("VALIDATION_ERROR", "name is required");
            }
            habit.setName(request.name().trim());
        }
        if (request.description() != null) {
            habit.setDescription(blankToNull(request.description()));
        }
        if (request.frequencyType() != null) {
            if (!VALID_FREQUENCY_TYPES.contains(request.frequencyType())) {
                throw new HabitException("VALIDATION_ERROR", "Invalid frequencyType: " + request.frequencyType());
            }
            habit.setFrequencyType(request.frequencyType());
        }
        if (request.frequencyDays() != null) {
            habit.setFrequencyDays(request.frequencyDays());
        }
        if (request.targetCount() != null) {
            habit.setTargetCount(Math.max(1, request.targetCount()));
        }
        if (request.color() != null) {
            habit.setColor(blankToNull(request.color()));
        }
        if (request.icon() != null) {
            habit.setIcon(blankToNull(request.icon()));
        }
        if (request.archived() != null) {
            habit.setArchived(request.archived());
        }

        habit.setUpdatedAt(Instant.now());
        habitStore.save(habit);
        return HabitResponse.from(habit);
    }

    public void archiveHabit(String userId, String id) {
        Habit habit = findOwnedHabit(userId, id);
        habitStore.deleteById(id);
        refreshTodayStats(userId);
    }

    public HabitCheckinResponse checkin(String userId, String habitId, CreateCheckinRequest request) {
        Habit habit = findOwnedHabit(userId, habitId);
        LocalDate today = request != null && request.checkinDate() != null
                ? request.checkinDate()
                : TimeSupport.today();

        HabitCheckin existing = checkinStore.findByHabitIdAndDate(habitId, today).orElse(null);
        if (existing != null) {
            existing.setCount(request != null && request.count() > 0 ? request.count() : existing.getCount() + 1);
            existing.setNote(request != null && request.note() != null ? request.note() : existing.getNote());
            existing.setUpdatedAt(Instant.now());
            checkinStore.save(existing);
            refreshTodayStats(userId);
            return HabitCheckinResponse.from(existing);
        }

        HabitCheckin checkin = new HabitCheckin();
        checkin.setUserId(userId);
        checkin.setHabitId(habitId);
        checkin.setCheckinDate(today);
        checkin.setCount(request != null && request.count() > 0 ? request.count() : 1);
        checkin.setNote(request == null ? null : blankToNull(request.note()));

        checkinStore.save(checkin);
        refreshTodayStats(userId);
        return HabitCheckinResponse.from(checkin);
    }

    public void cancelCheckin(String userId, String habitId, LocalDate checkinDate) {
        findOwnedHabit(userId, habitId);
        LocalDate targetDate = checkinDate != null ? checkinDate : TimeSupport.today();
        HabitCheckin existing = checkinStore.findByHabitIdAndDate(habitId, targetDate)
                .orElseThrow(() -> new HabitException("NOT_FOUND", "Habit checkin not found"));
        if (!userId.equals(existing.getUserId())) {
            throw new HabitException("FORBIDDEN", "Access denied");
        }
        checkinStore.deleteByHabitIdAndDate(habitId, targetDate);
        refreshTodayStats(userId);
    }

    public List<HabitCheckinResponse> listCheckins(String userId, String habitId, LocalDate from, LocalDate to) {
        findOwnedHabit(userId, habitId);
        return checkinStore.findByHabitId(habitId).stream()
                .filter(c -> from == null || !c.getCheckinDate().isBefore(from))
                .filter(c -> to == null || !c.getCheckinDate().isAfter(to))
                .map(HabitCheckinResponse::from)
                .toList();
    }

    private Habit findOwnedHabit(String userId, String id) {
        Habit habit = habitStore.findById(id)
                .orElseThrow(() -> new HabitException("NOT_FOUND", "Habit not found"));
        if (!habit.getUserId().equals(userId)) {
            throw new HabitException("FORBIDDEN", "Access denied");
        }
        return habit;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void refreshTodayStats(String userId) {
        LocalDate today = TimeSupport.today();
        long total = habitStore.findByUserId(userId).stream()
                .filter(h -> !h.isArchived())
                .count();
        long completed = checkinStore.findByUserIdAndDate(userId, today).stream()
                .filter(c -> c.getCount() > 0)
                .map(HabitCheckin::getHabitId)
                .distinct()
                .count();
        statsStore.setHabitTodayStats(userId, completed, total);
    }
}
