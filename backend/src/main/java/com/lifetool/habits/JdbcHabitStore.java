package com.lifetool.habits;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class JdbcHabitStore implements HabitStore {

    private final String url;
    private final String username;
    private final String password;

    public JdbcHabitStore(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public Habit save(Habit habit) {
        String sql = """
                INSERT INTO habits (id, user_id, name, description, frequency_type, frequency_days,
                  target_count, color, icon, is_archived, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                  name = EXCLUDED.name,
                  description = EXCLUDED.description,
                  frequency_type = EXCLUDED.frequency_type,
                  frequency_days = EXCLUDED.frequency_days,
                  target_count = EXCLUDED.target_count,
                  color = EXCLUDED.color,
                  icon = EXCLUDED.icon,
                  is_archived = EXCLUDED.is_archived,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, habit.getId());
            stmt.setString(2, habit.getUserId());
            stmt.setString(3, habit.getName());
            stmt.setString(4, habit.getDescription());
            stmt.setString(5, habit.getFrequencyType());
            int[] days = habit.getFrequencyDays();
            if (days != null && days.length > 0) {
                Integer[] boxed = new Integer[days.length];
                for (int i = 0; i < days.length; i++) {
                    boxed[i] = days[i];
                }
                stmt.setArray(6, conn.createArrayOf("int", boxed));
            } else {
                stmt.setNull(6, java.sql.Types.ARRAY);
            }
            stmt.setInt(7, habit.getTargetCount());
            stmt.setString(8, habit.getColor());
            stmt.setString(9, habit.getIcon());
            stmt.setBoolean(10, habit.isArchived());
            stmt.setTimestamp(11, Timestamp.from(habit.getCreatedAt()));
            stmt.setTimestamp(12, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
            return habit;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save habit", ex);
        }
    }

    @Override
    public Optional<Habit> findById(String id) {
        String sql = """
                SELECT id, user_id, name, description, frequency_type, frequency_days,
                  target_count, color, icon, is_archived, created_at, updated_at
                FROM habits
                WHERE id = ?::uuid AND deleted_at IS NULL
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapHabit(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find habit", ex);
        }
    }

    @Override
    public List<Habit> findByUserId(String userId) {
        String sql = """
                SELECT id, user_id, name, description, frequency_type, frequency_days,
                  target_count, color, icon, is_archived, created_at, updated_at
                FROM habits
                WHERE user_id = ?::uuid AND deleted_at IS NULL
                ORDER BY created_at DESC
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Habit> habits = new ArrayList<>();
                while (rs.next()) {
                    habits.add(mapHabit(rs));
                }
                return habits;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find habits", ex);
        }
    }

    @Override
    public void deleteById(String id) {
        String sql = "UPDATE habits SET deleted_at = now(), updated_at = now() WHERE id = ?::uuid";
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete habit", ex);
        }
    }

    private Habit mapHabit(ResultSet rs) throws SQLException {
        Habit habit = new Habit();
        habit.setId(rs.getString("id"));
        habit.setUserId(rs.getString("user_id"));
        habit.setName(rs.getString("name"));
        habit.setDescription(rs.getString("description"));
        habit.setFrequencyType(rs.getString("frequency_type"));
        var arr = rs.getArray("frequency_days");
        if (arr != null) {
            Object[] objArr = (Object[]) arr.getArray();
            int[] days = new int[objArr.length];
            for (int i = 0; i < objArr.length; i++) {
                days[i] = ((Number) objArr[i]).intValue();
            }
            habit.setFrequencyDays(days);
        }
        habit.setTargetCount(rs.getInt("target_count"));
        habit.setColor(rs.getString("color"));
        habit.setIcon(rs.getString("icon"));
        habit.setArchived(rs.getBoolean("is_archived"));
        habit.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        habit.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return habit;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
