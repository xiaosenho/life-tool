package com.lifetool.leaderboards;

public interface LeaderboardStatsStore {
    long getFocusTodaySeconds(String userId);

    void setFocusTodaySeconds(String userId, long seconds);

    long getFocusWeekSeconds(String userId);

    void setFocusWeekSeconds(String userId, long seconds);

    long getHabitsTodayCompletion(String userId);

    void setHabitsTodayCompletion(String userId, long completion);

    default void setHabitTodayStats(String userId, long completed, long total) {
        setHabitsTodayCompletion(userId, completed);
    }

    long getStreaksDays(String userId);

    void setStreaksDays(String userId, long days);

    void clearAll();
}
