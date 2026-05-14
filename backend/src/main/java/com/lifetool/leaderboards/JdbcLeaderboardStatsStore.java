package com.lifetool.leaderboards;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class JdbcLeaderboardStatsStore implements LeaderboardStatsStore {

    private final String url;
    private final String username;
    private final String password;

    public JdbcLeaderboardStatsStore(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public long getFocusTodaySeconds(String userId) {
        String sql = """
                SELECT COALESCE(focus_total_seconds, 0) AS val
                FROM daily_stats
                WHERE user_id = ?::uuid AND stat_date = CURRENT_DATE
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("val");
                }
                return 0L;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to get focus today seconds", ex);
        }
    }

    @Override
    public void setFocusTodaySeconds(String userId, long seconds) {
        String sql = """
                INSERT INTO daily_stats (id, user_id, stat_date, focus_total_seconds, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, CURRENT_DATE, ?, now(), now())
                ON CONFLICT (user_id, stat_date) DO UPDATE SET
                  focus_total_seconds = EXCLUDED.focus_total_seconds,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, userId);
            stmt.setLong(3, seconds);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to set focus today seconds", ex);
        }
    }

    @Override
    public long getFocusWeekSeconds(String userId) {
        String sql = """
                SELECT COALESCE(SUM(focus_total_seconds), 0) AS val
                FROM daily_stats
                WHERE user_id = ?::uuid
                  AND stat_date >= CURRENT_DATE - INTERVAL '6 days'
                  AND stat_date <= CURRENT_DATE
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("val");
                }
                return 0L;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to get focus week seconds", ex);
        }
    }

    @Override
    public void setFocusWeekSeconds(String userId, long seconds) {
        String sql = """
                INSERT INTO daily_stats (id, user_id, stat_date, focus_total_seconds, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, CURRENT_DATE, ?, now(), now())
                ON CONFLICT (user_id, stat_date) DO UPDATE SET
                  focus_total_seconds = EXCLUDED.focus_total_seconds,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, userId);
            stmt.setLong(3, seconds);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to set focus week seconds", ex);
        }
    }

    @Override
    public long getHabitsTodayCompletion(String userId) {
        String sql = """
                SELECT COALESCE(habit_completed_count, 0) AS val
                FROM daily_stats
                WHERE user_id = ?::uuid AND stat_date = CURRENT_DATE
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("val");
                }
                return 0L;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to get habits today completion", ex);
        }
    }

    @Override
    public void setHabitsTodayCompletion(String userId, long completion) {
        String sql = """
                INSERT INTO daily_stats (id, user_id, stat_date, habit_completed_count, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, CURRENT_DATE, ?, now(), now())
                ON CONFLICT (user_id, stat_date) DO UPDATE SET
                  habit_completed_count = EXCLUDED.habit_completed_count,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, userId);
            stmt.setLong(3, completion);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to set habits today completion", ex);
        }
    }

    @Override
    public void setHabitTodayStats(String userId, long completed, long total) {
        String sql = """
                INSERT INTO daily_stats (id, user_id, stat_date, habit_completed_count, habit_total_count, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, CURRENT_DATE, ?, ?, now(), now())
                ON CONFLICT (user_id, stat_date) DO UPDATE SET
                  habit_completed_count = EXCLUDED.habit_completed_count,
                  habit_total_count = EXCLUDED.habit_total_count,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, userId);
            stmt.setLong(3, completed);
            stmt.setLong(4, total);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to set habit today stats", ex);
        }
    }

    @Override
    public long getStreaksDays(String userId) {
        String sql = """
                SELECT COALESCE(habit_streak_days, 0) AS val
                FROM daily_stats
                WHERE user_id = ?::uuid AND stat_date = CURRENT_DATE
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("val");
                }
                return 0L;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to get streaks days", ex);
        }
    }

    @Override
    public void setStreaksDays(String userId, long days) {
        String sql = """
                INSERT INTO daily_stats (id, user_id, stat_date, habit_streak_days, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, CURRENT_DATE, ?, now(), now())
                ON CONFLICT (user_id, stat_date) DO UPDATE SET
                  habit_streak_days = EXCLUDED.habit_streak_days,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, userId);
            stmt.setLong(3, days);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to set streaks days", ex);
        }
    }

    @Override
    public void clearAll() {
        String sql = "DELETE FROM daily_stats";
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to clear daily stats", ex);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
