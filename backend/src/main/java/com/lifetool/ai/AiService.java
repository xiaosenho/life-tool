package com.lifetool.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lifetool.ai.dto.AiChatMessageResponse;
import com.lifetool.ai.dto.AiChatMessagesResponse;
import com.lifetool.ai.dto.AiChatSessionResponse;
import com.lifetool.ai.dto.AiMemoriesResponse;
import com.lifetool.ai.dto.AiMemoryResponse;
import com.lifetool.ai.dto.AiToolCallStatusResponse;
import com.lifetool.ai.dto.CreateAiChatSessionRequest;
import com.lifetool.ai.dto.FoodRecognitionRequest;
import com.lifetool.ai.dto.FoodRecognitionResponse;
import com.lifetool.ai.dto.LifeAdviceRequest;
import com.lifetool.ai.dto.LifeAdviceResponse;
import com.lifetool.ai.dto.SendAiMessageRequest;
import com.lifetool.media.MediaService;
import com.lifetool.meals.MealLog;
import com.lifetool.meals.MealService;

@Service
public class AiService {
    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final String SYSTEM_PROMPT = """
            你是 LifeTool AI 助手，帮助用户管理专注、习惯、饮食、记账和纪念日。
            规则：
            - 只使用已授权的用户数据工具读取汇总信息。
            - 不直接修改用户数据。
            - 返回简洁、可执行的中文建议。
            - 涉及健康、财务或法律问题时，明确说明仅供参考。
            """;

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
    private final AiAssistantClient assistantClient;
    private final MealService mealService;
    private final MediaService mediaService;

    public AiService(AiChatStore chatStore, AiMemoryStore memoryStore,
                     UserDataTools userDataTools, AiProperties properties,
                     AiAssistantClient assistantClient, MealService mealService, MediaService mediaService) {
        this.chatStore = chatStore;
        this.memoryStore = memoryStore;
        this.userDataTools = userDataTools;
        this.properties = properties;
        this.assistantClient = assistantClient;
        this.mealService = mealService;
        this.mediaService = mediaService;
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

        String systemPrompt = buildSystemPrompt(session.isUseLongTermMemory(), userId);
        List<AiAssistantClient.ChatEntry> history = chatStore.listMessages(sessionId).stream()
                .map(m -> new AiAssistantClient.ChatEntry(m.getRole(), m.getContent()))
                .toList();
        List<AiAssistantClient.ToolResult> toolResults = toolCalls.stream()
                .map(tc -> new AiAssistantClient.ToolResult(tc.getToolName(), tc.getResultSummary()))
                .toList();

        String assistantContent;
        try {
            UserDataTools.setCurrentUserId(userId);
            assistantContent = assistantClient.chat(sessionId, systemPrompt, history, toolResults);
        } finally {
            UserDataTools.clearCurrentUserId();
        }

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

        String systemPrompt = buildSystemPrompt(true, userId);
        String userQuery = "请根据我的近期生活数据，给我一些综合生活建议和分析。";

        List<AiAssistantClient.ChatEntry> history = List.of(
                new AiAssistantClient.ChatEntry("user", userQuery));
        List<AiAssistantClient.ToolResult> toolResults = toolCalls.stream()
                .map(tc -> new AiAssistantClient.ToolResult(tc.getToolName(), tc.getResultSummary()))
                .toList();

        String advice;
        try {
            UserDataTools.setCurrentUserId(userId);
            advice = assistantClient.chat("life-advice-" + userId, systemPrompt, history, toolResults);
        } finally {
            UserDataTools.clearCurrentUserId();
        }

        List<String> suggestions = List.of(advice);
        List<String> domains = toolCalls.stream().map(AiToolCall::getToolName).toList();
        return new LifeAdviceResponse(
                "已结合你的近期生活数据生成建议，当前可用数据域：" + String.join("、", domains) + "。",
                suggestions,
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
        memoryStore.save(memory);
    }

    public FoodRecognitionResponse recognizeFood(String userId, FoodRecognitionRequest request) {
        String systemPrompt = "你是一个专业的营养师。请识别用户上传图片中的食物，估算每种食物的重量、热量（千卡）以及蛋白质/脂肪/碳水化合物含量（克），最后必须用“总热量约 N 千卡”给出总热量估算。";

        String userText = request.customPrompt() != null && !request.customPrompt().isBlank()
                ? request.customPrompt()
                : "请识别图片中的食物并估算热量";

        String imageUrl = resolveFoodImageUrl(userId, request);
        String response;
        try {
            UserDataTools.setCurrentUserId(userId);
            response = chatWithImageRetryOnExpiredSignedUrl(userId, request, systemPrompt, userText, imageUrl);
        } catch (RuntimeException ex) {
            throw new AiException("AI_RECOGNITION_FAILED", "AI 识图失败，请确认图片可以访问或稍后重试");
        } finally {
            UserDataTools.clearCurrentUserId();
        }

        MealLog mealLog = mealService.recordAiRecognition(
                userId,
                response,
                request.mealType(),
                request.mediaAssetId());
        return new FoodRecognitionResponse(
                response,
                properties.getDisclaimer(),
                mealLog.getId(),
                mealLog.getTotalCalories());
    }

    private String chatWithImageRetryOnExpiredSignedUrl(
            String userId,
            FoodRecognitionRequest request,
            String systemPrompt,
            String userText,
            String firstImageUrl) {
        try {
            return assistantClient.chatWithImage("food-" + userId, systemPrompt, firstImageUrl, userText);
        } catch (RuntimeException firstEx) {
            if (!isCosDownload403(firstEx)) {
                throw firstEx;
            }
            String refreshedImageUrl = resolveFoodImageUrl(userId, request);
            log.warn("AI image download got 403, retrying with refreshed signed URL. userId={}, mediaAssetId={}",
                    userId, request.mediaAssetId());
            return assistantClient.chatWithImage("food-" + userId, systemPrompt, refreshedImageUrl, userText);
        }
    }

    private boolean isCosDownload403(Throwable ex) {
        String message = ex.getMessage();
        if (!hasText(message)) {
            return false;
        }
        return message.contains("Error while downloading") && message.contains("status code: 403");
    }

    private String resolveFoodImageUrl(String userId, FoodRecognitionRequest request) {
        return mediaService.generateReadUrl(userId, request.mediaAssetId(), "meal_photo");
    }

    private String buildSystemPrompt(boolean useLongTermMemory, String userId) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT);
        if (useLongTermMemory) {
            sb.append("\n已启用长期记忆。");
            List<AiMemoryItem> memories = memoryStore.findEnabledByUserId(userId);
            if (!memories.isEmpty()) {
                sb.append("\n用户长期记忆：");
                for (AiMemoryItem m : memories) {
                    sb.append("\n- [").append(m.getType()).append("] ").append(m.getContent());
                }
            }
        }
        return sb.toString();
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
