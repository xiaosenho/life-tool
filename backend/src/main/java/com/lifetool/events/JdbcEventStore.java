package com.lifetool.events;

import java.sql.Connection;
import java.sql.Date;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
@Profile("postgres")
public class JdbcEventStore implements EventStore {

    private final String url;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper;

    public JdbcEventStore(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            ObjectMapper objectMapper) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.objectMapper = objectMapper;
    }

    @Override
    public AnniversaryEvent save(AnniversaryEvent event) {
        String sql = """
                INSERT INTO anniversary_events (id, user_id, event_type, title, event_date, \
                repeat_rule, remind_days_before, note, media_asset_id, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?::jsonb, ?, ?::uuid, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                  event_type = EXCLUDED.event_type,
                  title = EXCLUDED.title,
                  event_date = EXCLUDED.event_date,
                  repeat_rule = EXCLUDED.repeat_rule,
                  remind_days_before = EXCLUDED.remind_days_before,
                  note = EXCLUDED.note,
                  media_asset_id = EXCLUDED.media_asset_id,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, event.getId());
            stmt.setString(2, event.getUserId());
            stmt.setString(3, event.getType());
            stmt.setString(4, event.getTitle());
            stmt.setDate(5, event.getEventDate() != null ? Date.valueOf(event.getEventDate()) : null);
            stmt.setString(6, event.getRepeatRule());
            stmt.setString(7, serializeReminders(event.getRemindDaysBefore()));
            stmt.setString(8, event.getNote());
            stmt.setString(9, event.getMediaAssetId());
            stmt.setTimestamp(10, event.getCreatedAt() != null ? Timestamp.from(event.getCreatedAt()) : null);
            stmt.setTimestamp(11, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
            return event;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save event", ex);
        }
    }

    @Override
    public Optional<AnniversaryEvent> findById(String id) {
        String sql = """
                SELECT id, user_id, event_type, title, event_date, repeat_rule, \
                remind_days_before, note, media_asset_id, created_at, updated_at
                FROM anniversary_events
                WHERE id = ?::uuid AND deleted_at IS NULL
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapEvent(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load event", ex);
        }
    }

    @Override
    public List<AnniversaryEvent> findByUserId(String userId) {
        String sql = """
                SELECT id, user_id, event_type, title, event_date, repeat_rule, \
                remind_days_before, note, media_asset_id, created_at, updated_at
                FROM anniversary_events
                WHERE user_id = ?::uuid AND deleted_at IS NULL
                """;
        List<AnniversaryEvent> events = new ArrayList<>();
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    events.add(mapEvent(rs));
                }
            }
            return events;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load events by user", ex);
        }
    }

    private AnniversaryEvent mapEvent(ResultSet rs) throws SQLException {
        AnniversaryEvent event = new AnniversaryEvent();
        event.setId(rs.getString("id"));
        event.setUserId(rs.getString("user_id"));
        event.setType(rs.getString("event_type"));
        event.setTitle(rs.getString("title"));
        event.setEventDate(rs.getDate("event_date") != null ? rs.getDate("event_date").toLocalDate() : null);
        event.setRepeatRule(rs.getString("repeat_rule"));
        event.setRemindDaysBefore(deserializeReminders(rs.getString("remind_days_before")));
        event.setNote(rs.getString("note"));
        event.setMediaAssetId(rs.getString("media_asset_id"));
        event.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null);
        event.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null);
        return event;
    }

    private String serializeReminders(List<Integer> reminders) {
        try {
            return objectMapper.writeValueAsString(reminders);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize remind days", ex);
        }
    }

    private List<Integer> deserializeReminders(String json) {
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize remind days", ex);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
