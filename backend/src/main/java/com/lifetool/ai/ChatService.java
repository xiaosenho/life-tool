package com.lifetool.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lifetool.ai.dto.ChatMessage;
import com.lifetool.ai.dto.ChatSession;
import com.lifetool.ai.store.ChatSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String SYSTEM_PROMPT = """
            你是一个友善的生活助手。用户会和你聊他们的日常生活、专注、习惯、饮食等话题。
            请给出温暖、鼓励的回应，适当给出建议。
            注意：你的建议仅供参考，不构成专业医疗或营养诊断。
            """;

    private final AiConfig config;
    private final DoubaoClient doubaoClient;
    private final DoubaoMockClient mockClient;
    private final ChatSessionStore sessionStore;
    private final ObjectMapper mapper;

    public ChatService(AiConfig config, DoubaoClient doubaoClient,
                       DoubaoMockClient mockClient, ChatSessionStore sessionStore, ObjectMapper mapper) {
        this.config = config;
        this.doubaoClient = doubaoClient;
        this.mockClient = mockClient;
        this.sessionStore = sessionStore;
        this.mapper = mapper;
    }

    public ChatSession createSession(String userId, String title) {
        ChatSession session = new ChatSession(userId, title != null ? title : "新对话");
        sessionStore.saveSession(session);
        return session;
    }

    public ChatMessage sendMessage(String userId, String sessionId, String content) {
        ChatSession session = sessionStore.findSessionById(sessionId)
                .orElseThrow(() -> new AiException("NOT_FOUND", "Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new AiException("FORBIDDEN", "Access denied");
        }

        ChatMessage userMsg = new ChatMessage(sessionId, "user", content);
        sessionStore.saveMessage(sessionId, userMsg);

        List<JsonNode> history = new ArrayList<>();
        List<ChatMessage> previousMessages = sessionStore.findMessagesBySessionId(sessionId);
        for (ChatMessage msg : previousMessages) {
            ObjectNode node = mapper.createObjectNode();
            node.put("role", msg.getRole());
            node.put("content", msg.getContent());
            history.add(node);
        }

        java.util.concurrent.CompletableFuture<JsonNode> future;
        if (config.isMockEnabled()) {
            future = mockClient.chatWithHistory(SYSTEM_PROMPT, history);
        } else {
            future = doubaoClient.chatWithHistory(SYSTEM_PROMPT, history);
        }

        String reply;
        try {
            JsonNode response = future.get();
            reply = doubaoClient.extractTextContent(response);
        } catch (Exception e) {
            log.error("Chat AI call failed", e);
            reply = "抱歉，我现在无法回复，请稍后再试。";
        }

        ChatMessage assistantMsg = new ChatMessage(sessionId, "assistant", reply);
        sessionStore.saveMessage(sessionId, assistantMsg);
        return assistantMsg;
    }

    public List<ChatSession> listSessions(String userId) {
        return sessionStore.findSessionsByUserId(userId);
    }

    public List<ChatMessage> listMessages(String userId, String sessionId) {
        ChatSession session = sessionStore.findSessionById(sessionId)
                .orElseThrow(() -> new AiException("NOT_FOUND", "Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new AiException("FORBIDDEN", "Access denied");
        }
        return sessionStore.findMessagesBySessionId(sessionId);
    }

    public void deleteSession(String userId, String sessionId) {
        ChatSession session = sessionStore.findSessionById(sessionId)
                .orElseThrow(() -> new AiException("NOT_FOUND", "Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new AiException("FORBIDDEN", "Access denied");
        }
        sessionStore.deleteSession(sessionId);
    }
}
