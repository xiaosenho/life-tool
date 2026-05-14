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
public class JdbcAiChatStore implements AiChatStore {

    private final String url;
    private final String username;
    private final String password;

    public JdbcAiChatStore(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public AiChatSession saveSession(AiChatSession session) {
        String sql = """
                INSERT INTO ai_chat_sessions (id, user_id, title, message_count, is_deleted, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?, 0, false, ?, ?)
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, session.getId());
            stmt.setString(2, session.getUserId());
            stmt.setString(3, session.getTitle());
            stmt.setTimestamp(4, Timestamp.from(session.getCreatedAt()));
            stmt.setTimestamp(5, Timestamp.from(session.getUpdatedAt()));
            stmt.executeUpdate();
            return session;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to save chat session", ex);
        }
    }

    @Override
    public Optional<AiChatSession> findSession(String id) {
        String sql = """
                SELECT id, user_id, title, created_at
                FROM ai_chat_sessions
                WHERE id = ?::uuid AND (is_deleted = false OR is_deleted IS NULL)
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String userId = rs.getString("user_id");
                String title = rs.getString("title");
                AiChatSession session = new AiChatSession(userId, title, true);
                setId(session, rs.getString("id"));
                return Optional.of(session);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to find chat session", ex);
        }
    }

    @Override
    public AiChatMessage appendMessage(AiChatMessage message) {
        String sql = """
                INSERT INTO ai_chat_messages (id, session_id, role, content, seq, created_at, updated_at)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, message.getId());
            stmt.setString(2, message.getSessionId());
            stmt.setString(3, message.getRole());
            stmt.setString(4, message.getContent());
            stmt.setInt(5, message.getSeq());
            Timestamp now = Timestamp.from(message.getCreatedAt());
            stmt.setTimestamp(6, now);
            stmt.setTimestamp(7, now);
            stmt.executeUpdate();

            String updateSql = "UPDATE ai_chat_sessions SET message_count = message_count + 1, updated_at = ? WHERE id = ?::uuid";
            try (var updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setTimestamp(1, now);
                updateStmt.setString(2, message.getSessionId());
                updateStmt.executeUpdate();
            }
            return message;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to append chat message", ex);
        }
    }

    @Override
    public int nextSeq(String sessionId) {
        String sql = "SELECT COALESCE(MAX(seq), 0) + 1 FROM ai_chat_messages WHERE session_id = ?::uuid";
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to get next seq", ex);
        }
        return 1;
    }

    @Override
    public List<AiChatMessage> listMessages(String sessionId) {
        String sql = """
                SELECT id, session_id, role, content, seq, created_at
                FROM ai_chat_messages
                WHERE session_id = ?::uuid
                ORDER BY seq
                """;
        List<AiChatMessage> messages = new ArrayList<>();
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AiChatMessage msg = new AiChatMessage(
                            rs.getString("session_id"),
                            "",
                            rs.getString("role"),
                            rs.getString("content"),
                            rs.getInt("seq"));
                    setId(msg, rs.getString("id"));
                    messages.add(msg);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list chat messages", ex);
        }
        return messages;
    }

    @Override
    public AiToolCall appendToolCall(AiToolCall toolCall) {
        String sql = """
                INSERT INTO ai_tool_calls (id, user_id, session_id, message_id, tool_name, arguments, result_summary, status, latency_ms, created_at)
                VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, toolCall.getId());
            stmt.setString(2, toolCall.getUserId());
            stmt.setString(3, toolCall.getSessionId());
            stmt.setString(4, toolCall.getMessageId());
            stmt.setString(5, toolCall.getToolName());
            stmt.setString(6, toJson(toolCall.getArguments()));
            stmt.setString(7, toJson(toolCall.getResultSummary()));
            stmt.setString(8, toolCall.getStatus());
            stmt.setLong(9, toolCall.getLatencyMs());
            stmt.setTimestamp(10, Timestamp.from(toolCall.getCreatedAt()));
            stmt.executeUpdate();
            return toolCall;
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to append tool call", ex);
        }
    }

    @Override
    public List<AiToolCall> listToolCalls(String messageId) {
        String sql = """
                SELECT id, user_id, session_id, message_id, tool_name, result_summary, status, latency_ms, created_at
                FROM ai_tool_calls
                WHERE message_id = ?::uuid
                ORDER BY created_at
                """;
        List<AiToolCall> calls = new ArrayList<>();
        try (Connection conn = getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, messageId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AiToolCall call = new AiToolCall(
                            rs.getString("user_id"),
                            rs.getString("session_id"),
                            rs.getString("message_id"),
                            rs.getString("tool_name"),
                            null,
                            null,
                            rs.getString("status"),
                            rs.getLong("latency_ms"));
                    setId(call, rs.getString("id"));
                    calls.add(call);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list tool calls", ex);
        }
        return calls;
    }

    private static String toJson(java.util.Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static void setId(Object obj, String id) {
        try {
            var field = obj.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(obj, id);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
