package com.lifetool.habits;

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class JdbcHabitCheckinStore implements HabitCheckinStore {

    private final DataSource dataSource;

    public JdbcHabitCheckinStore(
            DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public HabitCheckin save(HabitCheckin checkin) {
        String sql = """
                INSERT INTO habit_checkins (id, user_id, habit_id, checkin_date, count, note, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                  count = EXCLUDED.count,
                  note = EXCLUDED.note,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, checkin.getId());
            stmt.setString(2, checkin.getUserId());
            stmt.setString(3, checkin.getHabitId());
            stmt.setDate(4, Date.valueOf(checkin.getCheckinDate()));
            stmt.setInt(5, checkin.getCount());
            stmt.setString(6, checkin.getNote());
            stmt.setTimestamp(7, Timestamp.from(checkin.getCreatedAt()));
            stmt.setTimestamp(8, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
            return checkin;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save habit checkin", ex);
        }
    }

    @Override
    public Optional<HabitCheckin> findByHabitIdAndDate(String habitId, LocalDate date) {
        String sql = """
                SELECT id, user_id, habit_id, checkin_date, count, note, created_at, updated_at
                FROM habit_checkins
                WHERE habit_id = ?::uuid AND checkin_date = ?
                LIMIT 1
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, habitId);
            stmt.setDate(2, Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapCheckin(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find habit checkin", ex);
        }
    }

    @Override
    public void deleteByHabitIdAndDate(String habitId, LocalDate date) {
        String sql = """
                DELETE FROM habit_checkins
                WHERE habit_id = ?::uuid AND checkin_date = ?
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, habitId);
            stmt.setDate(2, Date.valueOf(date));
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete habit checkin", ex);
        }
    }

    @Override
    public List<HabitCheckin> findByHabitId(String habitId) {
        String sql = """
                SELECT id, user_id, habit_id, checkin_date, count, note, created_at, updated_at
                FROM habit_checkins
                WHERE habit_id = ?::uuid
                ORDER BY checkin_date DESC
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, habitId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<HabitCheckin> checkins = new ArrayList<>();
                while (rs.next()) {
                    checkins.add(mapCheckin(rs));
                }
                return checkins;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find habit checkins", ex);
        }
    }

    @Override
    public List<HabitCheckin> findByUserIdAndDate(String userId, LocalDate date) {
        String sql = """
                SELECT id, user_id, habit_id, checkin_date, count, note, created_at, updated_at
                FROM habit_checkins
                WHERE user_id = ?::uuid AND checkin_date = ?
                ORDER BY created_at DESC
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setDate(2, Date.valueOf(date));
            try (ResultSet rs = stmt.executeQuery()) {
                List<HabitCheckin> checkins = new ArrayList<>();
                while (rs.next()) {
                    checkins.add(mapCheckin(rs));
                }
                return checkins;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find habit checkins by date", ex);
        }
    }

    @Override
    public List<HabitCheckin> findByUserIdAndDateRange(String userId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT id, user_id, habit_id, checkin_date, count, note, created_at, updated_at
                FROM habit_checkins
                WHERE user_id = ?::uuid
                  AND checkin_date >= ?
                  AND checkin_date <= ?
                ORDER BY checkin_date DESC, created_at DESC
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setDate(2, Date.valueOf(from));
            stmt.setDate(3, Date.valueOf(to));
            try (ResultSet rs = stmt.executeQuery()) {
                List<HabitCheckin> checkins = new ArrayList<>();
                while (rs.next()) {
                    checkins.add(mapCheckin(rs));
                }
                return checkins;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find habit checkins by date range", ex);
        }
    }

    private HabitCheckin mapCheckin(ResultSet rs) throws SQLException {
        HabitCheckin checkin = new HabitCheckin();
        checkin.setId(rs.getString("id"));
        checkin.setUserId(rs.getString("user_id"));
        checkin.setHabitId(rs.getString("habit_id"));
        checkin.setCheckinDate(rs.getDate("checkin_date").toLocalDate());
        checkin.setCount(rs.getInt("count"));
        checkin.setNote(rs.getString("note"));
        checkin.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        checkin.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return checkin;
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
