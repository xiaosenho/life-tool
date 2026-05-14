package com.lifetool.meals;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class JdbcMealStore implements MealStore {
    private final String url;
    private final String username;
    private final String password;

    public JdbcMealStore(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public MealLog saveAiMealLog(MealLog mealLog) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                insertMealLog(conn, mealLog);
                if (mealLog.getTotalCalories() != null) {
                    insertAiMealItem(conn, mealLog);
                }
                refreshTodayCalories(conn, mealLog.getUserId());
                conn.commit();
                return mealLog;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save meal log", ex);
        }
    }

    @Override
    public MealSummary getSummary(String userId) {
        String summarySql = """
                SELECT
                  COALESCE(SUM(total_calories) FILTER (
                    WHERE occurred_at >= CURRENT_DATE::timestamptz
                      AND occurred_at < (CURRENT_DATE + INTERVAL '1 day')::timestamptz
                  ), 0) AS today_calories,
                  COUNT(*) FILTER (
                    WHERE occurred_at >= CURRENT_DATE::timestamptz
                      AND occurred_at < (CURRENT_DATE + INTERVAL '1 day')::timestamptz
                  ) AS today_count,
                  COALESCE(SUM(total_calories), 0) AS week_calories,
                  COUNT(*) AS week_count
                FROM meal_logs
                WHERE user_id = ?::uuid
                  AND occurred_at >= (CURRENT_DATE - INTERVAL '6 days')::timestamptz
                  AND occurred_at < (CURRENT_DATE + INTERVAL '1 day')::timestamptz
                """;
        String recentSql = """
                SELECT id, meal_type, occurred_at, total_calories, is_ai_generated
                FROM meal_logs
                WHERE user_id = ?::uuid
                ORDER BY occurred_at DESC
                LIMIT 5
                """;
        try (Connection conn = getConnection();
             var summaryStmt = conn.prepareStatement(summarySql);
             var recentStmt = conn.prepareStatement(recentSql)) {
            summaryStmt.setString(1, userId);
            BigDecimal todayCalories = BigDecimal.ZERO;
            int todayCount = 0;
            BigDecimal weekCalories = BigDecimal.ZERO;
            int weekCount = 0;
            try (ResultSet rs = summaryStmt.executeQuery()) {
                if (rs.next()) {
                    todayCalories = rs.getBigDecimal("today_calories");
                    todayCount = rs.getInt("today_count");
                    weekCalories = rs.getBigDecimal("week_calories");
                    weekCount = rs.getInt("week_count");
                }
            }

            recentStmt.setString(1, userId);
            List<MealSummary.RecentMeal> recentMeals = new ArrayList<>();
            try (ResultSet rs = recentStmt.executeQuery()) {
                while (rs.next()) {
                    recentMeals.add(new MealSummary.RecentMeal(
                            rs.getString("id"),
                            rs.getString("meal_type"),
                            rs.getTimestamp("occurred_at").toInstant(),
                            rs.getBigDecimal("total_calories"),
                            rs.getBoolean("is_ai_generated")));
                }
            }
            return new MealSummary(todayCalories, todayCount, weekCalories, weekCount, recentMeals);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to get meal summary", ex);
        }
    }

    private void insertMealLog(Connection conn, MealLog mealLog) throws SQLException {
        String sql = """
                INSERT INTO meal_logs (id, user_id, meal_type, occurred_at, total_calories, note, media_asset_id,
                                       is_ai_generated, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?::uuid, ?, ?, ?)
                """;
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mealLog.getId());
            stmt.setString(2, mealLog.getUserId());
            stmt.setString(3, mealLog.getMealType());
            stmt.setTimestamp(4, Timestamp.from(mealLog.getOccurredAt()));
            stmt.setBigDecimal(5, mealLog.getTotalCalories());
            stmt.setString(6, mealLog.getNote());
            if (mealLog.getMediaAssetId() == null || mealLog.getMediaAssetId().isBlank()) {
                stmt.setNull(7, Types.OTHER);
            } else {
                stmt.setString(7, mealLog.getMediaAssetId());
            }
            stmt.setBoolean(8, mealLog.isAiGenerated());
            stmt.setTimestamp(9, Timestamp.from(mealLog.getCreatedAt()));
            stmt.setTimestamp(10, Timestamp.from(mealLog.getUpdatedAt()));
            stmt.executeUpdate();
        }
    }

    private void insertAiMealItem(Connection conn, MealLog mealLog) throws SQLException {
        String sql = """
                INSERT INTO meal_items (id, meal_log_id, food_name, calories, sort_order, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?, ?, 0, now(), now())
                """;
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, mealLog.getId());
            stmt.setString(3, "AI 识别饮食");
            stmt.setBigDecimal(4, mealLog.getTotalCalories());
            stmt.executeUpdate();
        }
    }

    private void refreshTodayCalories(Connection conn, String userId) throws SQLException {
        String sql = """
                INSERT INTO daily_stats (id, user_id, stat_date, meal_total_calories, created_at, updated_at)
                SELECT ?::uuid, ?::uuid, CURRENT_DATE, COALESCE(SUM(total_calories), 0), now(), now()
                FROM meal_logs
                WHERE user_id = ?::uuid
                  AND occurred_at >= CURRENT_DATE::timestamptz
                  AND occurred_at < (CURRENT_DATE + INTERVAL '1 day')::timestamptz
                ON CONFLICT (user_id, stat_date) DO UPDATE SET
                  meal_total_calories = EXCLUDED.meal_total_calories,
                  updated_at = now()
                """;
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, userId);
            stmt.setString(3, userId);
            stmt.executeUpdate();
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
