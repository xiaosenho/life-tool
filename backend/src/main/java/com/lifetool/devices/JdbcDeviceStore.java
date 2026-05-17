package com.lifetool.devices;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
@Profile("postgres")
public class JdbcDeviceStore implements DeviceStore {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DataSource dataSource;

    public JdbcDeviceStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Device save(Device device) {
        String sql = """
                INSERT INTO devices
                  (id, user_id, installation_id, device_name, device_type, push_token, vendor_device_id,
                   push_provider, push_enabled, push_bound_at, last_active_at, created_at, updated_at, metadata)
                VALUES (?::uuid, ?::uuid, ?, ?, ?::text, ?, ?, ?::text, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id) DO UPDATE SET
                  user_id = EXCLUDED.user_id,
                  installation_id = EXCLUDED.installation_id,
                  device_name = EXCLUDED.device_name,
                  device_type = EXCLUDED.device_type,
                  push_token = EXCLUDED.push_token,
                  vendor_device_id = EXCLUDED.vendor_device_id,
                  push_provider = EXCLUDED.push_provider,
                  push_enabled = EXCLUDED.push_enabled,
                  push_bound_at = EXCLUDED.push_bound_at,
                  last_active_at = EXCLUDED.last_active_at,
                  updated_at = now(),
                  metadata = EXCLUDED.metadata
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, device.getId());
            stmt.setString(2, device.getUserId());
            stmt.setString(3, device.getInstallationId());
            stmt.setString(4, device.getDeviceName());
            stmt.setString(5, device.getDeviceType().name().toLowerCase());
            stmt.setString(6, device.getPushToken());
            stmt.setString(7, device.getVendorDeviceId());
            stmt.setString(8, device.getPushProvider() == null ? null : device.getPushProvider().name().toLowerCase());
            stmt.setBoolean(9, device.isPushEnabled());
            stmt.setTimestamp(10, device.getPushBoundAt() == null ? null : Timestamp.from(device.getPushBoundAt()));
            stmt.setTimestamp(11, Timestamp.from(device.getLastActiveAt()));
            stmt.setTimestamp(12, Timestamp.from(device.getCreatedAt()));
            stmt.setTimestamp(13, Timestamp.from(device.getUpdatedAt()));
            stmt.setString(14, toJson(device.getMetadata()));
            stmt.executeUpdate();
            return device;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save device", ex);
        }
    }

    @Override
    public Optional<Device> findById(String id) {
        String sql = """
                SELECT id, user_id, installation_id, device_name, device_type, push_token, vendor_device_id,
                       push_provider, push_enabled, push_bound_at, last_active_at, created_at, updated_at, metadata
                FROM devices
                WHERE id = ?::uuid
                """;
        return queryOne(sql, id);
    }

    @Override
    public Optional<Device> findByInstallationId(String installationId) {
        String sql = """
                SELECT id, user_id, installation_id, device_name, device_type, push_token, vendor_device_id,
                       push_provider, push_enabled, push_bound_at, last_active_at, created_at, updated_at, metadata
                FROM devices
                WHERE installation_id = ?
                """;
        return queryOne(sql, installationId);
    }

    @Override
    public List<Device> findByUserId(String userId) {
        String sql = """
                SELECT id, user_id, installation_id, device_name, device_type, push_token, vendor_device_id,
                       push_provider, push_enabled, push_bound_at, last_active_at, created_at, updated_at, metadata
                FROM devices
                WHERE user_id = ?::uuid
                ORDER BY updated_at DESC
                """;
        List<Device> devices = new ArrayList<>();
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    devices.add(mapDevice(rs));
                }
            }
            return devices;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query devices", ex);
        }
    }

    private Optional<Device> queryOne(String sql, String value) {
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapDevice(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query device", ex);
        }
    }

    private Device mapDevice(ResultSet rs) throws SQLException {
        Timestamp pushBoundAt = rs.getTimestamp("push_bound_at");
        return new Device(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("installation_id"),
                rs.getString("device_name"),
                Device.DeviceType.valueOf(rs.getString("device_type").toUpperCase()),
                rs.getString("push_token"),
                rs.getString("vendor_device_id"),
                parsePushProvider(rs.getString("push_provider")),
                rs.getBoolean("push_enabled"),
                pushBoundAt == null ? null : pushBoundAt.toInstant(),
                rs.getTimestamp("last_active_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                parseJson(rs.getString("metadata"))
        );
    }

    private Device.PushProvider parsePushProvider(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Device.PushProvider.valueOf(value.toUpperCase());
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse device metadata", ex);
        }
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize device metadata", ex);
        }
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
