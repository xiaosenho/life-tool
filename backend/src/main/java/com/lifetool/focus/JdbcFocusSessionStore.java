package com.lifetool.focus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.lifetool.common.TimeSupport;

@Repository
@Profile("postgres")
public class JdbcFocusSessionStore implements FocusSessionStore {

    private final String url;
    private final String username;
    private final String password;

    public JdbcFocusSessionStore(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public FocusSession save(FocusSession session) {
        String sql = """
                INSERT INTO focus_sessions (id, user_id, mode, target_seconds, actual_seconds,
                  status, started_at, ended_at, note, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                  mode = EXCLUDED.mode,
                  target_seconds = EXCLUDED.target_seconds,
                  actual_seconds = EXCLUDED.actual_seconds,
                  status = EXCLUDED.status,
                  started_at = EXCLUDED.started_at,
                  ended_at = EXCLUDED.ended_at,
                  note = EXCLUDED.note,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, session.getId());
            stmt.setString(2, session.getUserId());
            stmt.setString(3, session.getMode());
            stmt.setInt(4, session.getTargetSeconds());
            stmt.setInt(5, session.getActualSeconds());
            stmt.setString(6, session.getStatus());
            stmt.setTimestamp(7, session.getStartedAt() != null ? Timestamp.from(session.getStartedAt()) : null);
            stmt.setTimestamp(8, session.getEndedAt() != null ? Timestamp.from(session.getEndedAt()) : null);
            stmt.setString(9, session.getNote());
            stmt.setTimestamp(10, Timestamp.from(session.getCreatedAt()));
            stmt.setTimestamp(11, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
            return session;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save focus session", ex);
        }
    }

    @Override
    public Optional<FocusSession> findById(String id) {
        String sql = """
                SELECT id, user_id, mode, target_seconds, actual_seconds, status,
                  started_at, ended_at, note, created_at, updated_at
                FROM focus_sessions
                WHERE id = ?::uuid
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapSession(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find focus session", ex);
        }
    }

    @Override
    public List<FocusSession> findByUserId(String userId) {
        String sql = """
                SELECT id, user_id, mode, target_seconds, actual_seconds, status,
                  started_at, ended_at, note, created_at, updated_at
                FROM focus_sessions
                WHERE user_id = ?::uuid
                ORDER BY started_at DESC
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<FocusSession> sessions = new ArrayList<>();
                while (rs.next()) {
                    sessions.add(mapSession(rs));
                }
                return sessions;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find focus sessions", ex);
        }
    }

    @Override
    public List<FocusSession> findByUserIdAndMonth(String userId, String month) {
        YearMonth ym = YearMonth.parse(month);
        Instant monthStart = TimeSupport.startOfMonth(ym);
        Instant monthEnd = TimeSupport.startOfNextMonth(ym);

        String sql = """
                SELECT id, user_id, mode, target_seconds, actual_seconds, status,
                  started_at, ended_at, note, created_at, updated_at
                FROM focus_sessions
                WHERE user_id = ?::uuid
                  AND started_at >= ?
                  AND started_at < ?
                ORDER BY started_at DESC
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setTimestamp(2, Timestamp.from(monthStart));
            stmt.setTimestamp(3, Timestamp.from(monthEnd));
            try (ResultSet rs = stmt.executeQuery()) {
                List<FocusSession> sessions = new ArrayList<>();
                while (rs.next()) {
                    sessions.add(mapSession(rs));
                }
                return sessions;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find focus sessions by month", ex);
        }
    }

    private FocusSession mapSession(ResultSet rs) throws SQLException {
        FocusSession session = new FocusSession();
        session.setId(rs.getString("id"));
        session.setUserId(rs.getString("user_id"));
        session.setMode(rs.getString("mode"));
        session.setTargetSeconds(rs.getInt("target_seconds"));
        session.setActualSeconds(rs.getInt("actual_seconds"));
        session.setStatus(rs.getString("status"));
        Timestamp startedAt = rs.getTimestamp("started_at");
        session.setStartedAt(startedAt != null ? startedAt.toInstant() : null);
        Timestamp endedAt = rs.getTimestamp("ended_at");
        session.setEndedAt(endedAt != null ? endedAt.toInstant() : null);
        session.setNote(rs.getString("note"));
        session.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        session.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return session;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
