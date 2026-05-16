package com.lifetool.ai;

import java.util.List;
import java.util.Optional;

public interface AiChatStore {

    AiChatSession saveSession(AiChatSession session);

    Optional<AiChatSession> findSession(String id);

    AiChatMessage appendMessage(AiChatMessage message);

    int nextSeq(String sessionId);

    List<AiChatMessage> listMessages(String sessionId);

    AiToolCall appendToolCall(AiToolCall toolCall);

    List<AiToolCall> listToolCalls(String messageId);
}
