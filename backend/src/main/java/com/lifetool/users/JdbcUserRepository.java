package com.lifetool.users;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class JdbcUserRepository implements UserRepository {

    private final String url;
    private final String username;
    private final String password;

    public JdbcUserRepository(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public Optional<User> findById(String id) {
        String sql = """
                SELECT id, email, password_hash, display_name, created_at
                FROM users
                WHERE id = ?::uuid AND deleted_at IS NULL
                """;
        return findOne(sql, id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = """
                SELECT id, email, password_hash, display_name, created_at
                FROM users
                WHERE lower(email) = lower(?) AND deleted_at IS NULL
                """;
        return findOne(sql, email);
    }

    @Override
    public User save(User user) {
        String sql = """
                INSERT INTO users (id, email, password_hash, display_name, created_at, updated_at)
                VALUES (?::uuid, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                  email = EXCLUDED.email,
                  password_hash = EXCLUDED.password_hash,
                  display_name = EXCLUDED.display_name,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getId());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPasswordHash());
            stmt.setString(4, user.getDisplayName());
            stmt.setTimestamp(5, Timestamp.from(user.getCreatedAt()));
            stmt.setTimestamp(6, Timestamp.from(Instant.now()));
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
                rs.getTimestamp("created_at").toInstant());
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
