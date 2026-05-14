package com.lifetool.friends;

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
public class JdbcFriendStore implements FriendStore {

    private final String url;
    private final String username;
    private final String password;

    public JdbcFriendStore(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public FriendRequest saveRequest(FriendRequest request) {
        String sql = """
                INSERT INTO friendships (id, requester_id, addressee_id, status, requested_at, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?::text, ?, now(), now())
                ON CONFLICT (id) DO UPDATE SET
                  status = EXCLUDED.status,
                  responded_at = CASE WHEN EXCLUDED.status <> 'pending' THEN now() ELSE friendships.responded_at END,
                  updated_at = now()
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, request.getId());
            stmt.setString(2, request.getFromUserId());
            stmt.setString(3, request.getToUserId());
            stmt.setString(4, statusToString(request.getStatus()));
            stmt.setTimestamp(5, Timestamp.from(request.getCreatedAt()));
            stmt.executeUpdate();
            return request;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save friend request", ex);
        }
    }

    @Override
    public Optional<FriendRequest> findRequestById(String id) {
        String sql = """
                SELECT id, requester_id, addressee_id, status, requested_at, updated_at
                FROM friendships
                WHERE id = ?::uuid AND deleted_at IS NULL
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRequest(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find friend request by id", ex);
        }
    }

    @Override
    public Optional<FriendRequest> findPendingRequest(String fromUserId, String toUserId) {
        String sql = """
                SELECT id, requester_id, addressee_id, status, requested_at, updated_at
                FROM friendships
                WHERE requester_id = ?::uuid AND addressee_id = ?::uuid
                  AND status = 'pending' AND deleted_at IS NULL
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fromUserId);
            stmt.setString(2, toUserId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRequest(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find pending request", ex);
        }
    }

    @Override
    public Optional<FriendRequest> findPendingRequestBetween(String userId1, String userId2) {
        String sql = """
                SELECT id, requester_id, addressee_id, status, requested_at, updated_at
                FROM friendships
                WHERE ((requester_id = ?::uuid AND addressee_id = ?::uuid)
                    OR (requester_id = ?::uuid AND addressee_id = ?::uuid))
                  AND status = 'pending' AND deleted_at IS NULL
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId1);
            stmt.setString(2, userId2);
            stmt.setString(3, userId2);
            stmt.setString(4, userId1);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRequest(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find pending request between users", ex);
        }
    }

    @Override
    public List<FriendRequest> findRequestsByUser(String userId) {
        String sql = """
                SELECT id, requester_id, addressee_id, status, requested_at, updated_at
                FROM friendships
                WHERE (requester_id = ?::uuid OR addressee_id = ?::uuid)
                  AND status = 'pending' AND deleted_at IS NULL
                """;
        List<FriendRequest> results = new ArrayList<>();
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRequest(rs));
                }
            }
            return results;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find requests by user", ex);
        }
    }

    @Override
    public Friendship saveFriendship(Friendship friendship) {
        String findSql = """
                SELECT id FROM friendships
                WHERE ((requester_id = ?::uuid AND addressee_id = ?::uuid)
                    OR (requester_id = ?::uuid AND addressee_id = ?::uuid))
                  AND status = 'pending' AND deleted_at IS NULL
                """;
        try (Connection conn = getConnection()) {
            String existingId = null;
            try (var findStmt = conn.prepareStatement(findSql)) {
                findStmt.setString(1, friendship.getUserId());
                findStmt.setString(2, friendship.getFriendUserId());
                findStmt.setString(3, friendship.getFriendUserId());
                findStmt.setString(4, friendship.getUserId());
                try (ResultSet rs = findStmt.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getString("id");
                    }
                }
            }

            if (existingId != null) {
                String updateSql = """
                        UPDATE friendships
                        SET status = 'accepted', responded_at = now(), updated_at = now()
                        WHERE id = ?::uuid
                        """;
                try (var updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setString(1, existingId);
                    updateStmt.executeUpdate();
                }
            } else {
                String insertSql = """
                        INSERT INTO friendships (id, requester_id, addressee_id, status, requested_at, created_at, updated_at)
                        VALUES (?::uuid, ?::uuid, ?::uuid, 'accepted', ?, now(), now())
                        """;
                try (var insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, friendship.getId());
                    insertStmt.setString(2, friendship.getUserId());
                    insertStmt.setString(3, friendship.getFriendUserId());
                    insertStmt.setTimestamp(4, Timestamp.from(friendship.getCreatedAt()));
                    insertStmt.executeUpdate();
                }
            }
            return friendship;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save friendship", ex);
        }
    }

    @Override
    public List<Friendship> findFriendships(String userId) {
        String sql = """
                SELECT id, requester_id, addressee_id, requested_at
                FROM friendships
                WHERE (requester_id = ?::uuid OR addressee_id = ?::uuid)
                  AND status = 'accepted' AND deleted_at IS NULL
                """;
        List<Friendship> results = new ArrayList<>();
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapFriendship(rs));
                }
            }
            return results;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find friendships", ex);
        }
    }

    @Override
    public boolean areFriends(String userId1, String userId2) {
        String sql = """
                SELECT 1 FROM friendships
                WHERE ((requester_id = ?::uuid AND addressee_id = ?::uuid)
                    OR (requester_id = ?::uuid AND addressee_id = ?::uuid))
                  AND status = 'accepted' AND deleted_at IS NULL
                LIMIT 1
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId1);
            stmt.setString(2, userId2);
            stmt.setString(3, userId2);
            stmt.setString(4, userId1);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to check friendship", ex);
        }
    }

    @Override
    public void removeFriendship(String userId, String friendUserId) {
        String sql = """
                UPDATE friendships
                SET status = 'deleted', deleted_at = now(), updated_at = now()
                WHERE ((requester_id = ?::uuid AND addressee_id = ?::uuid)
                    OR (requester_id = ?::uuid AND addressee_id = ?::uuid))
                  AND status = 'accepted' AND deleted_at IS NULL
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, friendUserId);
            stmt.setString(3, friendUserId);
            stmt.setString(4, userId);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to remove friendship", ex);
        }
    }

    private FriendRequest mapRequest(ResultSet rs) throws SQLException {
        FriendRequest request = new FriendRequest(
                rs.getString("requester_id"),
                rs.getString("addressee_id"));
        try {
            var idField = FriendRequest.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(request, rs.getString("id"));
        } catch (ReflectiveOperationException ignored) {
        }
        request.setStatus(stringToStatus(rs.getString("status")));
        return request;
    }

    private Friendship mapFriendship(ResultSet rs) throws SQLException {
        return new Friendship(
                rs.getString("requester_id"),
                rs.getString("addressee_id"));
    }

    private static String statusToString(FriendRequest.Status status) {
        return switch (status) {
            case PENDING -> "pending";
            case ACCEPTED -> "accepted";
            case REJECTED -> "rejected";
        };
    }

    private static FriendRequest.Status stringToStatus(String s) {
        return switch (s) {
            case "accepted" -> FriendRequest.Status.ACCEPTED;
            case "rejected" -> FriendRequest.Status.REJECTED;
            default -> FriendRequest.Status.PENDING;
        };
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
