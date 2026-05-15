package com.lifetool.friends;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
public class JdbcFriendMessageStore implements FriendMessageStore {

    private final String url;
    private final String username;
    private final String password;

    public JdbcFriendMessageStore(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public FriendMessage save(FriendMessage message) {
        String sql = """
                INSERT INTO friend_messages
                  (id, from_user_id, to_user_id, message_type, content, created_at, read_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?::text, ?, ?, ?, now())
                ON CONFLICT (id) DO UPDATE SET
                  read_at = EXCLUDED.read_at,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, message.getId());
            stmt.setString(2, message.getFromUserId());
            stmt.setString(3, message.getToUserId());
            stmt.setString(4, message.getType().name().toLowerCase());
            stmt.setString(5, message.getContent());
            stmt.setTimestamp(6, Timestamp.from(message.getCreatedAt()));
            stmt.setTimestamp(7, message.getReadAt() == null ? null : Timestamp.from(message.getReadAt()));
            stmt.executeUpdate();
            return message;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save friend message", ex);
        }
    }

    @Override
    public List<FriendMessage> listConversation(String userId, String friendUserId) {
        String sql = """
                SELECT id, from_user_id, to_user_id, message_type, content, created_at, read_at
                FROM friend_messages
                WHERE ((from_user_id = ?::uuid AND to_user_id = ?::uuid)
                    OR (from_user_id = ?::uuid AND to_user_id = ?::uuid))
                  AND deleted_at IS NULL
                ORDER BY created_at ASC
                """;
        return queryMessages(sql, userId, friendUserId, friendUserId, userId);
    }

    @Override
    public List<FriendMessage> listByUser(String userId) {
        String sql = """
                SELECT id, from_user_id, to_user_id, message_type, content, created_at, read_at
                FROM friend_messages
                WHERE (from_user_id = ?::uuid OR to_user_id = ?::uuid)
                  AND deleted_at IS NULL
                ORDER BY created_at DESC
                """;
        return queryMessages(sql, userId, userId);
    }

    @Override
    public int markConversationRead(String userId, String friendUserId) {
        String sql = """
                UPDATE friend_messages
                SET read_at = now(), updated_at = now()
                WHERE from_user_id = ?::uuid
                  AND to_user_id = ?::uuid
                  AND read_at IS NULL
                  AND deleted_at IS NULL
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, friendUserId);
            stmt.setString(2, userId);
            return stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to mark friend messages read", ex);
        }
    }

    private List<FriendMessage> queryMessages(String sql, String... params) {
        List<FriendMessage> results = new ArrayList<>();
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            for (int index = 0; index < params.length; index++) {
                stmt.setString(index + 1, params[index]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapMessage(rs));
                }
            }
            return results;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to query friend messages", ex);
        }
    }

    private FriendMessage mapMessage(ResultSet rs) throws SQLException {
        Timestamp readAt = rs.getTimestamp("read_at");
        return new FriendMessage(
                rs.getString("id"),
                rs.getString("from_user_id"),
                rs.getString("to_user_id"),
                FriendMessage.MessageType.valueOf(rs.getString("message_type").toUpperCase()),
                rs.getString("content"),
                rs.getTimestamp("created_at").toInstant(),
                readAt == null ? null : readAt.toInstant());
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
