package com.lifetool.leaderboards;

import com.lifetool.leaderboards.dto.LeaderboardEntryDto;
import com.lifetool.leaderboards.dto.LeaderboardResponse;
import com.lifetool.users.User;
import com.lifetool.users.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeaderboardService {

    private final LeaderboardStatsStore statsStore;
    private final UserRepository userRepository;

    public LeaderboardService(LeaderboardStatsStore statsStore, UserRepository userRepository) {
        this.statsStore = statsStore;
        this.userRepository = userRepository;
    }

    public LeaderboardResponse getFocusToday(String userId) {
        long value = statsStore.getFocusTodaySeconds(userId);
        LeaderboardEntryDto entry = buildEntry(userId, value, 1);
        return new LeaderboardResponse("today", "focus_seconds", List.of(entry));
    }

    public LeaderboardResponse getFocusWeek(String userId) {
        long value = statsStore.getFocusWeekSeconds(userId);
        LeaderboardEntryDto entry = buildEntry(userId, value, 1);
        return new LeaderboardResponse("week", "focus_seconds", List.of(entry));
    }

    public LeaderboardResponse getHabitsToday(String userId) {
        long value = statsStore.getHabitsTodayCompletion(userId);
        LeaderboardEntryDto entry = buildEntry(userId, value, 1);
        return new LeaderboardResponse("today", "habit_completion", List.of(entry));
    }

    public LeaderboardResponse getStreaks(String userId) {
        long value = statsStore.getStreaksDays(userId);
        LeaderboardEntryDto entry = buildEntry(userId, value, 1);
        return new LeaderboardResponse("all_time", "streak_days", List.of(entry));
    }

    private LeaderboardEntryDto buildEntry(String userId, long value, int rank) {
        User user = userRepository.findById(userId).orElse(null);
        String displayName = user != null ? user.getDisplayName() : "Unknown";
        return new LeaderboardEntryDto(userId, displayName, null, value, rank);
    }
}
