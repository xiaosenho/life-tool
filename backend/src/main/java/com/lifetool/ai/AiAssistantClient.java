package com.lifetool.ai;

import java.util.List;
import java.util.Map;

public interface AiAssistantClient {

    String chat(String conversationId, String systemPrompt, List<ChatEntry> history, List<ToolResult> toolResults);

    record ChatEntry(String role, String content) {}

    record ToolResult(String toolName, Map<String, Object> data) {}
}
