package com.lifetool.ai.store;

import com.lifetool.ai.dto.ChatMessage;
import com.lifetool.ai.dto.ChatSession;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class ChatSessionStore {

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessage>> messages = new ConcurrentHashMap<>();

    public ChatSession saveSession(ChatSession session) {
        sessions.put(session.getId(), session);
        messages.put(session.getId(), new ArrayList<>());
        return session;
    }

    public Optional<ChatSession> findSessionById(String id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public List<ChatSession> findSessionsByUserId(String userId) {
        return sessions.values().stream()
                .filter(s -> s.getUserId().equals(userId))
                .sorted(Comparator.comparing(ChatSession::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public ChatMessage saveMessage(String sessionId, ChatMessage message) {
        messages.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
        return message;
    }

    public List<ChatMessage> findMessagesBySessionId(String sessionId) {
        return messages.getOrDefault(sessionId, List.of());
    }

    public void deleteSession(String id) {
        sessions.remove(id);
        messages.remove(id);
    }
}
