package com.lifetool.leaderboards;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryLeaderboardStatsStore implements LeaderboardStatsStore {

    private final Map<String, Long> focusTodaySeconds = new ConcurrentHashMap<>();
    private final Map<String, Long> focusWeekSeconds = new ConcurrentHashMap<>();
    private final Map<String, Long> habitsTodayCompletion = new ConcurrentHashMap<>();
    private final Map<String, Long> streaksDays = new ConcurrentHashMap<>();

    @Override
    public long getFocusTodaySeconds(String userId) {
        return focusTodaySeconds.getOrDefault(userId, 0L);
    }

    @Override
    public void setFocusTodaySeconds(String userId, long seconds) {
        focusTodaySeconds.put(userId, seconds);
    }

    @Override
    public long getFocusWeekSeconds(String userId) {
        return focusWeekSeconds.getOrDefault(userId, 0L);
    }

    @Override
    public void setFocusWeekSeconds(String userId, long seconds) {
        focusWeekSeconds.put(userId, seconds);
    }

    @Override
    public long getHabitsTodayCompletion(String userId) {
        return habitsTodayCompletion.getOrDefault(userId, 0L);
    }

    @Override
    public void setHabitsTodayCompletion(String userId, long completion) {
        habitsTodayCompletion.put(userId, completion);
    }

    @Override
    public long getStreaksDays(String userId) {
        return streaksDays.getOrDefault(userId, 0L);
    }

    @Override
    public void setStreaksDays(String userId, long days) {
        streaksDays.put(userId, days);
    }

    @Override
    public void clearAll() {
        focusTodaySeconds.clear();
        focusWeekSeconds.clear();
        habitsTodayCompletion.clear();
        streaksDays.clear();
    }
}
