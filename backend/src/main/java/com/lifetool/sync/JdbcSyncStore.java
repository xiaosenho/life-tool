package com.lifetool.sync;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
@Profile("postgres")
public class JdbcSyncStore implements SyncStore {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcSyncStore(
            DataSource dataSource,
            ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<SyncEntityRecord> find(String userId, String entityType, String entityId) {
        String sql = """
                SELECT user_id, entity_type, entity_id, server_version, operation, payload
                FROM sync_mutations
                WHERE user_id = ?::uuid AND entity_type = ? AND entity_id = ?
                ORDER BY server_version DESC
                LIMIT 1
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, entityType);
            stmt.setString(3, entityId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRecord(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find sync entity", ex);
        }
    }

    @Override
    public SyncEntityRecord save(String userId, String entityType, String entityId, boolean deleted,
                                  JsonNode payload) {
        try (Connection conn = getConnection()) {
            long versionId;
            String versionSql = "INSERT INTO server_versions DEFAULT VALUES RETURNING id";
            try (var stmt = conn.prepareStatement(versionSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Failed to create server version");
                }
                versionId = rs.getLong("id");
            }

            String operation;
            if (deleted) {
                operation = "delete";
            } else {
                Optional<SyncEntityRecord> existing = find(userId, entityType, entityId);
                operation = existing.isPresent() ? "update" : "create";
            }

            String payloadStr = payload != null ? payload.toString() : "{}";
            String sql = """
                    INSERT INTO sync_mutations (user_id, entity_type, entity_id, operation, server_version, payload, created_at, updated_at)
                    VALUES (?::uuid, ?, ?, ?::text, ?, ?::jsonb, now(), now())
                    """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, userId);
                stmt.setString(2, entityType);
                stmt.setString(3, entityId);
                stmt.setString(4, operation);
                stmt.setLong(5, versionId);
                stmt.setString(6, payloadStr);
                stmt.executeUpdate();
            }

            return new SyncEntityRecord(userId, entityType, entityId, versionId, deleted, payload);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save sync mutation", ex);
        }
    }

    @Override
    public List<SyncEntityRecord> changesSince(String userId, long cursor, List<String> entityTypes) {
        StringBuilder sql = new StringBuilder("""
                SELECT user_id, entity_type, entity_id, server_version, operation, payload
                FROM sync_mutations
                WHERE user_id = ?::uuid AND server_version > ?
                """);
        if (entityTypes != null && !entityTypes.isEmpty()) {
            sql.append(" AND entity_type = ANY(?::text[])");
        }
        sql.append(" ORDER BY server_version");

        List<SyncEntityRecord> results = new ArrayList<>();
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql.toString())) {
            stmt.setString(1, userId);
            stmt.setLong(2, cursor);
            if (entityTypes != null && !entityTypes.isEmpty()) {
                var arr = conn.createArrayOf("text", entityTypes.toArray());
                stmt.setArray(3, arr);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRecord(rs));
                }
            }
            return results;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load changes", ex);
        }
    }

    @Override
    public long currentVersion() {
        String sql = "SELECT COALESCE(MAX(id), 0) FROM server_versions";
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0L;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to get current version", ex);
        }
    }

    private SyncEntityRecord mapRecord(ResultSet rs) throws SQLException {
        String operation = rs.getString("operation");
        boolean deleted = "delete".equals(operation);
        JsonNode payload;
        try {
            String payloadStr = rs.getString("payload");
            payload = payloadStr != null ? objectMapper.readTree(payloadStr) : objectMapper.createObjectNode();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse payload JSON", ex);
        }
        return new SyncEntityRecord(
                rs.getString("user_id"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getLong("server_version"),
                deleted,
                payload);
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
