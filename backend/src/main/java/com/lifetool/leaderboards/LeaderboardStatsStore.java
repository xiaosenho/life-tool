package com.lifetool.leaderboards;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class LeaderboardStatsStore {

    private final Map<String, Long> focusTodaySeconds = new ConcurrentHashMap<>();
    private final Map<String, Long> focusWeekSeconds = new ConcurrentHashMap<>();
    private final Map<String, Long> habitsTodayCompletion = new ConcurrentHashMap<>();
    private final Map<String, Long> streaksDays = new ConcurrentHashMap<>();

    public long getFocusTodaySeconds(String userId) {
        return focusTodaySeconds.getOrDefault(userId, 0L);
    }

    public void setFocusTodaySeconds(String userId, long seconds) {
        focusTodaySeconds.put(userId, seconds);
    }

    public long getFocusWeekSeconds(String userId) {
        return focusWeekSeconds.getOrDefault(userId, 0L);
    }

    public void setFocusWeekSeconds(String userId, long seconds) {
        focusWeekSeconds.put(userId, seconds);
    }

    public long getHabitsTodayCompletion(String userId) {
        return habitsTodayCompletion.getOrDefault(userId, 0L);
    }

    public void setHabitsTodayCompletion(String userId, long completion) {
        habitsTodayCompletion.put(userId, completion);
    }

    public long getStreaksDays(String userId) {
        return streaksDays.getOrDefault(userId, 0L);
    }

    public void setStreaksDays(String userId, long days) {
        streaksDays.put(userId, days);
    }

    public void clearAll() {
        focusTodaySeconds.clear();
        focusWeekSeconds.clear();
        habitsTodayCompletion.clear();
        streaksDays.clear();
    }
}
