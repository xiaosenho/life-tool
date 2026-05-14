package com.lifetool.focus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class JdbcFocusPreferenceStore implements FocusPreferenceStore {

    private final String url;
    private final String username;
    private final String password;

    public JdbcFocusPreferenceStore(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public FocusPreference save(FocusPreference preference) {
        String sql = """
                INSERT INTO focus_preferences (id, user_id, default_focus_minutes, short_break_minutes,
                  long_break_minutes, auto_start_break, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, now(), now())
                ON CONFLICT (user_id) DO UPDATE SET
                  default_focus_minutes = EXCLUDED.default_focus_minutes,
                  short_break_minutes = EXCLUDED.short_break_minutes,
                  long_break_minutes = EXCLUDED.long_break_minutes,
                  auto_start_break = EXCLUDED.auto_start_break,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, preference.getUserId());
            stmt.setInt(3, preference.getDefaultFocusMinutes());
            stmt.setInt(4, preference.getShortBreakMinutes());
            stmt.setInt(5, preference.getLongBreakMinutes());
            stmt.setBoolean(6, preference.isAutoStartBreak());
            stmt.executeUpdate();
            return preference;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save focus preference", ex);
        }
    }

    @Override
    public Optional<FocusPreference> findByUserId(String userId) {
        String sql = """
                SELECT user_id, default_focus_minutes, short_break_minutes,
                  long_break_minutes, auto_start_break, updated_at
                FROM focus_preferences
                WHERE user_id = ?::uuid
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                FocusPreference pref = new FocusPreference();
                pref.setUserId(rs.getString("user_id"));
                pref.setDefaultFocusMinutes(rs.getInt("default_focus_minutes"));
                pref.setShortBreakMinutes(rs.getInt("short_break_minutes"));
                pref.setLongBreakMinutes(rs.getInt("long_break_minutes"));
                pref.setAutoStartBreak(rs.getBoolean("auto_start_break"));
                pref.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
                return Optional.of(pref);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find focus preference", ex);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
