package com.lifetool.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.lifetool.ai.dto.AiChatMessageResponse;
import com.lifetool.ai.dto.AiChatMessagesResponse;
import com.lifetool.ai.dto.AiChatSessionResponse;
import com.lifetool.ai.dto.AiMemoriesResponse;
import com.lifetool.ai.dto.AiMemoryResponse;
import com.lifetool.ai.dto.AiToolCallStatusResponse;
import com.lifetool.ai.dto.CreateAiChatSessionRequest;
import com.lifetool.ai.dto.LifeAdviceRequest;
import com.lifetool.ai.dto.LifeAdviceResponse;
import com.lifetool.ai.dto.SendAiMessageRequest;

@Service
public class AiService {
    private static final List<String> DEFAULT_TOOLS = List.of(
            "get_focus_summary",
            "get_habit_summary",
            "get_diet_summary",
            "get_ledger_summary",
            "get_upcoming_events",
            "get_user_profile_context");

    private final AiChatStore chatStore;
    private final AiMemoryStore memoryStore;
    private final UserDataTools userDataTools;
    private final AiProperties properties;

    public AiService(AiChatStore chatStore, AiMemoryStore memoryStore,
                     UserDataTools userDataTools, AiProperties properties) {
        this.chatStore = chatStore;
        this.memoryStore = memoryStore;
        this.userDataTools = userDataTools;
        this.properties = properties;
    }

    public AiChatSessionResponse createSession(String userId, CreateAiChatSessionRequest request) {
        String title = request.title() == null || request.title().isBlank()
                ? "新的 AI 对话"
                : request.title().trim();
        boolean useLongTermMemory = request.useLongTermMemory() == null || request.useLongTermMemory();
        AiChatSession session = chatStore.saveSession(new AiChatSession(userId, title, useLongTermMemory));
        seedDefaultMemory(userId);
        return AiChatSessionResponse.from(session);
    }

    public AiChatMessageResponse sendMessage(String userId, String sessionId, SendAiMessageRequest request) {
        AiChatSession session = findOwnedSession(userId, sessionId);
        AiChatMessage userMessage = chatStore.appendMessage(new AiChatMessage(
                sessionId, userId, "user", request.content().trim(), chatStore.nextSeq(sessionId)));

        List<AiToolCall> toolCalls = executeTools(userId, sessionId, userMessage.getId(), request.enabledTools());
        String assistantContent = buildAssistantReply(request.content(), toolCalls, session.isUseLongTermMemory());
        AiChatMessage assistantMessage = chatStore.appendMessage(new AiChatMessage(
                sessionId, userId, "assistant", assistantContent, chatStore.nextSeq(sessionId)));
        session.touch();

        return AiChatMessageResponse.from(
                assistantMessage,
                properties.getDisclaimer(),
                toolCalls.stream().map(AiToolCallStatusResponse::from).toList());
    }

    public AiChatMessagesResponse listMessages(String userId, String sessionId) {
        findOwnedSession(userId, sessionId);
        List<AiChatMessageResponse> messages = chatStore.listMessages(sessionId).stream()
                .map(message -> AiChatMessageResponse.from(
                        message,
                        "assistant".equals(message.getRole()) ? properties.getDisclaimer() : null,
                        chatStore.listToolCalls(message.getId()).stream()
                                .map(AiToolCallStatusResponse::from)
                                .toList()))
                .toList();
        return new AiChatMessagesResponse(messages);
    }

    public LifeAdviceResponse getLifeAdvice(String userId, LifeAdviceRequest request) {
        List<AiToolCall> toolCalls = executeTools(userId, null, "life-advice", request == null ? null : request.topics());
        List<String> domains = toolCalls.stream().map(AiToolCall::getToolName).toList();
        return new LifeAdviceResponse(
                "已结合你的近期生活数据生成建议，当前可用数据域：" + String.join("、", domains) + "。",
                List.of(
                        "先固定一个每天最容易完成的专注时段，降低开始成本。",
                        "饮食、记账和纪念日记录保持轻量补充，AI 才能逐步给出更贴近你的建议。",
                        "涉及健康、财务或重要决策时，把 AI 建议当作参考，不替代专业判断。"),
                properties.getDisclaimer());
    }

    public AiMemoriesResponse listMemories(String userId) {
        return new AiMemoriesResponse(memoryStore.findEnabledByUserId(userId).stream()
                .map(AiMemoryResponse::from)
                .toList());
    }

    public void deleteMemory(String userId, String id) {
        AiMemoryItem memory = memoryStore.findById(id)
                .orElseThrow(() -> new AiException("NOT_FOUND", "Memory not found"));
        if (!memory.getUserId().equals(userId)) {
            throw new AiException("FORBIDDEN", "Access denied");
        }
        memory.disable();
    }

    private AiChatSession findOwnedSession(String userId, String sessionId) {
        AiChatSession session = chatStore.findSession(sessionId)
                .orElseThrow(() -> new AiException("NOT_FOUND", "AI session not found"));
        if (session.isDeleted()) {
            throw new AiException("NOT_FOUND", "AI session not found");
        }
        if (!session.getUserId().equals(userId)) {
            throw new AiException("FORBIDDEN", "Access denied");
        }
        return session;
    }

    private List<AiToolCall> executeTools(String userId, String sessionId, String messageId, List<String> requestedTools) {
        Set<String> tools = new LinkedHashSet<>(normalizeRequestedTools(requestedTools));
        List<AiToolCall> executed = new ArrayList<>();
        int limit = Math.max(1, properties.getMaxToolRounds());
        for (String toolName : tools) {
            if (!DEFAULT_TOOLS.contains(toolName) || executed.size() >= limit) {
                continue;
            }
            long startedAt = System.currentTimeMillis();
            Map<String, Object> result = userDataTools.execute(toolName, userId);
            AiToolCall toolCall = chatStore.appendToolCall(new AiToolCall(
                    userId,
                    sessionId,
                    messageId,
                    toolName,
                    Map.of("scope", "current_user"),
                    result,
                    "succeeded",
                    System.currentTimeMillis() - startedAt));
            executed.add(toolCall);
        }
        return executed;
    }

    private String buildAssistantReply(String userContent, List<AiToolCall> toolCalls, boolean useLongTermMemory) {
        String memoryText = useLongTermMemory ? "已启用长期记忆。" : "本次未使用长期记忆。";
        String toolText = toolCalls.isEmpty()
                ? "本次没有读取额外数据。"
                : "我已读取 " + toolCalls.stream().map(AiToolCall::getToolName).toList() + " 的汇总数据。";
        return "收到：" + userContent.strip() + "\n" + toolText + "\n" + memoryText
                + "\n建议先从一个最小可执行动作开始，并在记录数据后继续让我结合趋势复盘。";
    }

    private List<String> normalizeRequestedTools(List<String> requestedTools) {
        if (requestedTools == null || requestedTools.isEmpty()) {
            return DEFAULT_TOOLS;
        }
        return requestedTools.stream()
                .map(tool -> switch (tool) {
                    case "focus" -> "get_focus_summary";
                    case "habit", "habits" -> "get_habit_summary";
                    case "diet" -> "get_diet_summary";
                    case "ledger" -> "get_ledger_summary";
                    case "event", "events" -> "get_upcoming_events";
                    case "profile" -> "get_user_profile_context";
                    default -> tool;
                })
                .toList();
    }

    private void seedDefaultMemory(String userId) {
        if (memoryStore.findEnabledByUserId(userId).isEmpty()) {
            memoryStore.save(new AiMemoryItem(userId, "preference", "默认优先返回中文、简洁、可执行的生活建议。", "system_extracted"));
        }
    }
}
