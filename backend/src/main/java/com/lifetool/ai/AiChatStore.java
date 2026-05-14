package com.lifetool.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class AiChatStore {
    private final Map<String, AiChatSession> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, List<AiChatMessage>> messagesBySessionId = new ConcurrentHashMap<>();
    private final Map<String, List<AiToolCall>> toolCallsByMessageId = new ConcurrentHashMap<>();

    public AiChatSession saveSession(AiChatSession session) {
        sessionsById.put(session.getId(), session);
        return session;
    }

    public Optional<AiChatSession> findSession(String id) {
        return Optional.ofNullable(sessionsById.get(id));
    }

    public AiChatMessage appendMessage(AiChatMessage message) {
        messagesBySessionId.computeIfAbsent(message.getSessionId(), key -> new ArrayList<>()).add(message);
        return message;
    }

    public int nextSeq(String sessionId) {
        return messagesBySessionId.getOrDefault(sessionId, List.of()).size() + 1;
    }

    public List<AiChatMessage> listMessages(String sessionId) {
        return messagesBySessionId.getOrDefault(sessionId, List.of()).stream()
                .sorted(Comparator.comparingInt(AiChatMessage::getSeq))
                .toList();
    }

    public AiToolCall appendToolCall(AiToolCall toolCall) {
        toolCallsByMessageId.computeIfAbsent(toolCall.getMessageId(), key -> new ArrayList<>()).add(toolCall);
        return toolCall;
    }

    public List<AiToolCall> listToolCalls(String messageId) {
        return toolCallsByMessageId.getOrDefault(messageId, List.of()).stream()
                .sorted(Comparator.comparing(AiToolCall::getCreatedAt))
                .toList();
    }
}
