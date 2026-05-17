package com.lifetool.users;

import java.util.Collection;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class JdbcUserRepository implements UserRepository {

    private final DataSource dataSource;

    public JdbcUserRepository(
            DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<User> findById(String id) {
        String sql = """
                SELECT id, email, password_hash, display_name, avatar_asset_id, created_at
                FROM users
                WHERE id = ?::uuid AND deleted_at IS NULL
                """;
        return findOne(sql, id);
    }

    @Override
    public Map<String, User> findByIds(Collection<String> ids) {
        List<String> uniqueIds = ids == null
                ? List.of()
                : ids.stream()
                        .filter(id -> id != null && !id.isBlank())
                        .distinct()
                        .toList();
        if (uniqueIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = uniqueIds.stream()
                .map(id -> "?::uuid")
                .collect(Collectors.joining(", "));
        String sql = """
                SELECT id, email, password_hash, display_name, avatar_asset_id, created_at
                FROM users
                WHERE id IN (%s) AND deleted_at IS NULL
                """.formatted(placeholders);
        Map<String, User> results = new LinkedHashMap<>();
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            for (int index = 0; index < uniqueIds.size(); index++) {
                stmt.setString(index + 1, uniqueIds.get(index));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    User user = mapUser(rs);
                    results.put(user.getId(), user);
                }
            }
            return results;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load users", ex);
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = """
                SELECT id, email, password_hash, display_name, avatar_asset_id, created_at
                FROM users
                WHERE lower(email) = lower(?) AND deleted_at IS NULL
                """;
        return findOne(sql, email);
    }

    @Override
    public User save(User user) {
        String sql = """
                INSERT INTO users (id, email, password_hash, display_name, avatar_asset_id, created_at, updated_at)
                VALUES (?::uuid, ?, ?, ?, ?::uuid, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                  email = EXCLUDED.email,
                  password_hash = EXCLUDED.password_hash,
                  display_name = EXCLUDED.display_name,
                  avatar_asset_id = EXCLUDED.avatar_asset_id,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getId());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPasswordHash());
            stmt.setString(4, user.getDisplayName());
            stmt.setString(5, user.getAvatarAssetId());
            stmt.setTimestamp(6, Timestamp.from(user.getCreatedAt()));
            stmt.setTimestamp(7, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
            return user;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save user", ex);
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        String sql = "SELECT 1 FROM users WHERE lower(email) = lower(?) AND deleted_at IS NULL LIMIT 1";
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to check user email", ex);
        }
    }

    private Optional<User> findOne(String sql, String value) {
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, value);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapUser(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load user", ex);
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("avatar_asset_id"),
                rs.getTimestamp("created_at").toInstant());
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
