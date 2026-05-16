package com.lifetool.ai;

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
public class JdbcAiMemoryStore implements AiMemoryStore {

    private final String url;
    private final String username;
    private final String password;

    public JdbcAiMemoryStore(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public AiMemoryItem save(AiMemoryItem memory) {
        String sql = """
                INSERT INTO ai_memory_items (id, user_id, memory_type, content, source, enabled, created_at, updated_at, deleted_at)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                  memory_type = EXCLUDED.memory_type,
                  content = EXCLUDED.content,
                  source = EXCLUDED.source,
                  enabled = EXCLUDED.enabled,
                  deleted_at = EXCLUDED.deleted_at,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, memory.getId());
            stmt.setString(2, memory.getUserId());
            stmt.setString(3, memory.getType());
            stmt.setString(4, memory.getContent());
            stmt.setString(5, memory.getSource());
            stmt.setBoolean(6, memory.isEnabled());
            Timestamp now = Timestamp.from(memory.getCreatedAt());
            stmt.setTimestamp(7, now);
            stmt.setTimestamp(8, now);
            stmt.setTimestamp(9, memory.getDeletedAt() != null ? Timestamp.from(memory.getDeletedAt()) : null);
            stmt.executeUpdate();
            return memory;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save AI memory", ex);
        }
    }

    @Override
    public Optional<AiMemoryItem> findById(String id) {
        String sql = """
                SELECT id, user_id, memory_type, content, source, enabled, deleted_at
                FROM ai_memory_items
                WHERE id = ?::uuid AND deleted_at IS NULL
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(mapMemory(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find AI memory", ex);
        }
    }

    @Override
    public List<AiMemoryItem> findEnabledByUserId(String userId) {
        String sql = """
                SELECT id, user_id, memory_type, content, source, enabled, deleted_at
                FROM ai_memory_items
                WHERE user_id = ?::uuid AND enabled = true AND deleted_at IS NULL
                ORDER BY created_at DESC
                """;
        List<AiMemoryItem> result = new ArrayList<>();
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapMemory(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list AI memories", ex);
        }
        return result;
    }

    private static AiMemoryItem mapMemory(ResultSet rs) throws SQLException {
        AiMemoryItem memory = new AiMemoryItem(
                rs.getString("user_id"),
                rs.getString("memory_type"),
                rs.getString("content"),
                rs.getString("source"));
        setField(memory, "id", rs.getString("id"));
        setField(memory, "enabled", rs.getBoolean("enabled"));
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (deletedAt != null) {
            setField(memory, "deletedAt", deletedAt.toInstant());
        }
        return memory;
    }

    private static void setField(AiMemoryItem memory, String fieldName, Object value) {
        try {
            var field = AiMemoryItem.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(memory, value);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
