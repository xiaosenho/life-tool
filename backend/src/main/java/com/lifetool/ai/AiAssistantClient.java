package com.lifetool.ai;

import java.util.List;
import java.util.Map;

public interface AiAssistantClient {

    String chat(String conversationId, String systemPrompt, List<ChatEntry> history, List<ToolResult> toolResults);

    String chatWithImage(String conversationId, String systemPrompt, String imageUrl, String userText);

    String chatWithMedia(String conversationId, String systemPrompt, List<ChatEntry> history,
                         List<ToolResult> toolResults, MediaInput mediaInput);

    record ChatEntry(String role, String content) {}

    record ToolResult(String toolName, Map<String, Object> data) {}

    record MediaInput(String kind, String url, String contentType) {}
}
