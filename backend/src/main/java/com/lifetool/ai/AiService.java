package com.lifetool.ai;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lifetool.ai.dto.AiChatMessageResponse;
import com.lifetool.ai.dto.AiChatMessagesResponse;
import com.lifetool.ai.dto.AiChatSessionResponse;
import com.lifetool.ai.dto.AiChatAttachmentRequest;
import com.lifetool.ai.dto.AiMemoriesResponse;
import com.lifetool.ai.dto.AiMemoryResponse;
import com.lifetool.ai.dto.AiToolCallStatusResponse;
import com.lifetool.ai.dto.CreateAiChatSessionRequest;
import com.lifetool.ai.dto.FoodRecognitionRequest;
import com.lifetool.ai.dto.FoodRecognitionResponse;
import com.lifetool.ai.dto.LifeAdviceRequest;
import com.lifetool.ai.dto.LifeAdviceResponse;
import com.lifetool.ai.dto.SendAiMessageRequest;
import com.lifetool.media.MediaAsset;
import com.lifetool.media.MediaService;
import com.lifetool.meals.MealLog;
import com.lifetool.meals.MealService;

@Service
public class AiService {
    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final int FOOD_IMAGE_RETRY_TIMES = 3;
    private static final long FOOD_IMAGE_RETRY_DELAY_MS = 1200L;
    private static final String SYSTEM_PROMPT = """
            你是 LifeTool AI 助手，帮助用户管理专注、习惯、饮食、记账和纪念日。
            规则：
            - 只使用已授权的用户数据工具读取汇总信息。
            - 不直接修改用户数据。
            - 仅当用户明确表达长期稳定偏好（例如“以后都用简洁中文回答我”“请记住我更关注减脂饮食”）时，调用 save_long_term_memory 保存长期记忆。
            - 不要保存临时要求、一次性上下文或模糊表达。
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
        AiChatAttachment attachment = buildAttachment(userId, request.attachment());
        String userContent = normalizeUserContent(request.content(), attachment);
        AiChatMessage userMessage = chatStore.appendMessage(new AiChatMessage(
                sessionId, userId, "user", userContent, attachment, chatStore.nextSeq(sessionId)));

        List<AiToolCall> toolCalls = executeTools(userId, sessionId, userMessage.getId(), request.enabledTools());

        String systemPrompt = buildSystemPrompt(session.isUseLongTermMemory(), userId);
        List<AiAssistantClient.ChatEntry> history = chatStore.listMessages(sessionId).stream()
                .map(m -> new AiAssistantClient.ChatEntry(m.getRole(), m.getContent()))
                .toList();
        List<AiAssistantClient.ToolResult> toolResults = toolCalls.stream()
                .map(tc -> new AiAssistantClient.ToolResult(tc.getToolName(), tc.getResultSummary()))
                .toList();

        String assistantContent;
        List<Map<String, Object>> savedMemoryEvents = List.of();
        try {
            UserDataTools.setCurrentUserId(userId);
            UserDataTools.resetSavedMemoryEvents();
            if (attachment != null) {
                AiChatAttachment requestAttachment = refreshAttachment(userId, attachment);
                assistantContent = assistantClient.chatWithMedia(
                        sessionId,
                        systemPrompt,
                        history,
                        toolResults,
                        new AiAssistantClient.MediaInput(
                                requestAttachment.kind(),
                                requestAttachment.url(),
                                requestAttachment.contentType(),
                                requestAttachment.assetId(),
                                userId));
            } else {
                assistantContent = assistantClient.chat(sessionId, systemPrompt, history, toolResults);
            }
            savedMemoryEvents = UserDataTools.consumeSavedMemoryEvents();
        } finally {
            if (savedMemoryEvents.isEmpty()) {
                UserDataTools.consumeSavedMemoryEvents();
            }
            UserDataTools.clearCurrentUserId();
        }

        AiChatMessage assistantMessage = chatStore.appendMessage(new AiChatMessage(
                sessionId, userId, "assistant", assistantContent, chatStore.nextSeq(sessionId)));
        appendSavedMemoryToolCalls(userId, sessionId, assistantMessage.getId(), savedMemoryEvents);
        session.touch();

        return toMessageResponse(
                userId,
                assistantMessage,
                properties.getDisclaimer(),
                mergeToolCallStatuses(toolCalls, savedMemoryEvents),
                !savedMemoryEvents.isEmpty());
    }

    private String normalizeUserContent(String content, AiChatAttachment attachment) {
        String normalized = content == null ? "" : content.trim();
        if (attachment == null && normalized.isBlank()) {
            throw new AiException("VALIDATION_ERROR", "content is required");
        }
        if (!normalized.isBlank()) {
            return normalized;
        }
        return attachment != null && "audio".equals(attachment.kind()) ? "[语音消息]" : "[图片消息]";
    }

    private AiChatAttachment buildAttachment(String userId, AiChatAttachmentRequest request) {
        if (request == null || request.assetId() == null || request.assetId().isBlank()) {
            return null;
        }
        MediaAsset asset = mediaService.findOwnedAsset(userId, request.assetId());
        boolean audio = asset.getContentType().startsWith("audio/");
        return new AiChatAttachment(
                asset.getId(),
                audio ? "audio" : "image",
                asset.getContentType(),
                null,
                request.width(),
                request.height(),
                request.durationSeconds());
    }

    public AiChatMessageResponse toMessageResponse(
            String userId,
            AiChatMessage message,
            String disclaimer,
            List<AiToolCallStatusResponse> toolCalls,
            boolean longTermMemorySaved) {
        return AiChatMessageResponse.from(
                message,
                refreshAttachment(userId, message.getAttachment()),
                disclaimer,
                toolCalls,
                longTermMemorySaved);
    }

    public AiChatMessagesResponse listMessages(String userId, String sessionId) {
        findOwnedSession(userId, sessionId);
        List<AiChatMessageResponse> messages = chatStore.listMessages(sessionId).stream()
                .map(message -> toMessageResponse(
                        userId,
                        message,
                        "assistant".equals(message.getRole()) ? properties.getDisclaimer() : null,
                        chatStore.listToolCalls(message.getId()).stream()
                                .map(AiToolCallStatusResponse::from)
                                .toList(),
                        hasSavedMemoryToolCall(message.getId())))
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
            UserDataTools.resetSavedMemoryEvents();
            advice = assistantClient.chat("life-advice-" + userId, systemPrompt, history, toolResults);
            UserDataTools.consumeSavedMemoryEvents();
        } finally {
            UserDataTools.consumeSavedMemoryEvents();
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
        String systemPrompt = """
                你是一个专业的营养师。请先判断图片主体是否包含可记录饮食的食物或饮品。
                如果图片不是食物/饮品，或无法确认是食物/饮品，只回复“未识别到食物，不会记录饮食。总热量约 0 千卡”，不要编造热量。
                如果确认是食物/饮品，请识别每种食物，估算重量、热量（千卡）以及蛋白质/脂肪/碳水化合物含量（克），最后必须用“总热量约 N 千卡”给出总热量估算。
                """;

        String userText = request.customPrompt() != null && !request.customPrompt().isBlank()
                ? request.customPrompt()
                : "请识别图片中的食物并估算热量";

        String imageUrl = resolveFoodImageUrl(userId, request);
        log.info(
                "AI food recognition start. userId={}, mediaAssetId={}, mealType={}, imagePath={}, qSignTime={}, keyTime={}",
                userId,
                request.mediaAssetId(),
                request.mealType(),
                summarizeImageUrl(imageUrl),
                extractQueryParam(imageUrl, "q-sign-time").orElse("<missing>"),
                extractQueryParam(imageUrl, "q-key-time").orElse("<missing>"));
        String response;
        try {
            UserDataTools.setCurrentUserId(userId);
            response = chatWithImageRetryOnExpiredSignedUrl(userId, request, systemPrompt, userText, imageUrl);
        } catch (RuntimeException ex) {
            log.error(
                    "AI food recognition failed. userId={}, mediaAssetId={}, imagePath={}, qSignTime={}, message={}",
                    userId,
                    request.mediaAssetId(),
                    summarizeImageUrl(imageUrl),
                    extractQueryParam(imageUrl, "q-sign-time").orElse("<missing>"),
                    ex.getMessage(),
                    ex);
            throw new AiException("AI_RECOGNITION_FAILED", "AI 识图失败，请确认图片可以访问或稍后重试");
        } finally {
            UserDataTools.clearCurrentUserId();
        }

        MealLog mealLog = null;
        if (MealService.shouldPersistAiRecognition(response)) {
            mealLog = mealService.recordAiRecognition(
                    userId,
                    response,
                    request.mealType(),
                    request.mediaAssetId());
        } else {
            log.info(
                    "AI food recognition skipped meal persistence. userId={}, mediaAssetId={}, mealType={}, totalCalories={}",
                    userId,
                    request.mediaAssetId(),
                    request.mealType(),
                    MealService.extractTotalCalories(response).map(Object::toString).orElse("<unknown>"));
        }
        return new FoodRecognitionResponse(
                response,
                properties.getDisclaimer(),
                mealLog == null ? null : mealLog.getId(),
                mealLog == null ? MealService.extractTotalCalories(response).orElse(null) : mealLog.getTotalCalories());
    }

    private String chatWithImageRetryOnExpiredSignedUrl(
            String userId,
            FoodRecognitionRequest request,
            String systemPrompt,
            String userText,
            String firstImageUrl) {
        String currentImageUrl = firstImageUrl;
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= FOOD_IMAGE_RETRY_TIMES; attempt++) {
            try {
                log.info(
                        "AI image recognition request. attempt={}/{}, userId={}, mediaAssetId={}, imagePath={}, qSignTime={}",
                        attempt,
                        FOOD_IMAGE_RETRY_TIMES,
                        userId,
                        request.mediaAssetId(),
                        summarizeImageUrl(currentImageUrl),
                        extractQueryParam(currentImageUrl, "q-sign-time").orElse("<missing>"));
                return assistantClient.chatWithImage("food-" + userId, systemPrompt, currentImageUrl, userText);
            } catch (RuntimeException ex) {
                lastException = ex;
                if (!isCosDownload403(ex) || attempt >= FOOD_IMAGE_RETRY_TIMES) {
                    log.warn(
                            "AI image recognition request failed without retry. attempt={}/{}, userId={}, mediaAssetId={}, imagePath={}, message={}",
                            attempt,
                            FOOD_IMAGE_RETRY_TIMES,
                            userId,
                            request.mediaAssetId(),
                            summarizeImageUrl(currentImageUrl),
                            ex.getMessage());
                    throw ex;
                }
                log.warn(
                        "AI image download returned 403, refreshing signed URL. nextAttempt={}/{}, userId={}, mediaAssetId={}, imagePath={}, failedQSignTime={}",
                        attempt + 1,
                        FOOD_IMAGE_RETRY_TIMES,
                        userId,
                        request.mediaAssetId(),
                        summarizeImageUrl(currentImageUrl),
                        extractQueryParam(currentImageUrl, "q-sign-time").orElse("<missing>"));
                sleepBeforeFoodRetry();
                currentImageUrl = resolveFoodImageUrl(userId, request);
                log.info(
                        "AI image recognition signed URL refreshed. userId={}, mediaAssetId={}, imagePath={}, refreshedQSignTime={}",
                        userId,
                        request.mediaAssetId(),
                        summarizeImageUrl(currentImageUrl),
                        extractQueryParam(currentImageUrl, "q-sign-time").orElse("<missing>"));
            }
        }
        throw lastException == null ? new RuntimeException("AI image recognition failed") : lastException;
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

    private void sleepBeforeFoodRetry() {
        try {
            Thread.sleep(FOOD_IMAGE_RETRY_DELAY_MS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while retrying AI image recognition", interruptedException);
        }
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

    private List<AiToolCallStatusResponse> mergeToolCallStatuses(
            List<AiToolCall> toolCalls,
            List<Map<String, Object>> savedMemoryEvents) {
        List<AiToolCallStatusResponse> statuses = new ArrayList<>(
                toolCalls.stream().map(AiToolCallStatusResponse::from).toList());
        if (!savedMemoryEvents.isEmpty()) {
            statuses.add(new AiToolCallStatusResponse("save_long_term_memory", "succeeded"));
        }
        return statuses;
    }

    private void appendSavedMemoryToolCalls(
            String userId,
            String sessionId,
            String messageId,
            List<Map<String, Object>> savedMemoryEvents) {
        for (Map<String, Object> event : savedMemoryEvents) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("memoryType", event.get("memoryType"));
            arguments.put("content", event.get("content"));
            chatStore.appendToolCall(new AiToolCall(
                    userId,
                    sessionId,
                    messageId,
                    "save_long_term_memory",
                    arguments,
                    event,
                    "succeeded",
                    0L));
        }
    }

    private boolean hasSavedMemoryToolCall(String messageId) {
        return chatStore.listToolCalls(messageId).stream()
                .anyMatch(toolCall -> "save_long_term_memory".equals(toolCall.getToolName())
                        && "succeeded".equals(toolCall.getStatus()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Optional<String> extractQueryParam(String url, String key) {
        if (!hasText(url) || !hasText(key)) {
            return Optional.empty();
        }
        int queryIndex = url.indexOf('?');
        if (queryIndex < 0 || queryIndex >= url.length() - 1) {
            return Optional.empty();
        }
        String query = url.substring(queryIndex + 1);
        for (String pair : query.split("&")) {
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }
            if (key.equals(pair.substring(0, equalsIndex))) {
                return Optional.of(pair.substring(equalsIndex + 1));
            }
        }
        return Optional.empty();
    }

    private String summarizeImageUrl(String url) {
        if (!hasText(url)) {
            return "<empty>";
        }
        int queryIndex = url.indexOf('?');
        return queryIndex >= 0 ? url.substring(0, queryIndex) : url;
    }

    private AiChatAttachment refreshAttachment(String userId, AiChatAttachment attachment) {
        if (attachment == null || attachment.assetId() == null || attachment.assetId().isBlank()) {
            return attachment;
        }
        try {
            String purpose = "audio".equals(attachment.kind()) ? "chat_audio" : "chat_image";
            return new AiChatAttachment(
                    attachment.assetId(),
                    attachment.kind(),
                    attachment.contentType(),
                    mediaService.generateReadUrl(userId, attachment.assetId(), purpose),
                    attachment.width(),
                    attachment.height(),
                    attachment.durationSeconds());
        } catch (RuntimeException ex) {
            return attachment;
        }
    }
}
