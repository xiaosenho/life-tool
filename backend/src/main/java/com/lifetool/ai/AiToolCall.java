package com.lifetool.ai;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class AiToolCall {
    private final String id = UUID.randomUUID().toString();
    private final String userId;
    private final String sessionId;
    private final String messageId;
    private final String toolName;
    private final Map<String, Object> arguments;
    private final Map<String, Object> resultSummary;
    private final String status;
    private final long latencyMs;
    private final Instant createdAt = Instant.now();

    public AiToolCall(String userId, String sessionId, String messageId, String toolName,
                      Map<String, Object> arguments, Map<String, Object> resultSummary,
                      String status, long latencyMs) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.messageId = messageId;
        this.toolName = toolName;
        this.arguments = arguments;
        this.resultSummary = resultSummary;
        this.status = status;
        this.latencyMs = latencyMs;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public Map<String, Object> getResultSummary() {
        return resultSummary;
    }

    public String getStatus() {
        return status;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
