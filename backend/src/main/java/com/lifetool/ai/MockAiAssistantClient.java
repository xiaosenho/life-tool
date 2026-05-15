package com.lifetool.ai;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MockAiAssistantClient implements AiAssistantClient {
    private static final Pattern EXPLICIT_PREFERENCE_PATTERN = Pattern.compile(
            "(以后|默认|总是|请记住|记住我|今后).*(简洁|中文|可执行|步骤|重点|结论|少讲理论|详细|减脂|预算)",
            Pattern.CASE_INSENSITIVE);

    private final UserDataTools userDataTools;

    public MockAiAssistantClient() {
        this.userDataTools = null;
    }

    public MockAiAssistantClient(UserDataTools userDataTools) {
        this.userDataTools = userDataTools;
    }

    @Override
    public String chatWithImage(String conversationId, String systemPrompt, String imageUrl, String userText) {
        return "【Mock】图片识别：" + (userText != null ? userText : "识别食物")
                + "。识别到米饭、鸡蛋和青菜，估算总热量约 520 千卡。";
    }

    @Override
    public String chatWithMedia(String conversationId, String systemPrompt, List<ChatEntry> history,
                                List<ToolResult> toolResults, MediaInput mediaInput) {
        String lastUserContent = history.stream()
                .filter(e -> "user".equals(e.role()))
                .reduce((a, b) -> b)
                .map(ChatEntry::content)
                .orElse("");
        String kind = mediaInput == null ? "无附件" : ("audio".equals(mediaInput.kind()) ? "语音" : "图片");
        return "收到你的" + kind + "消息：" + lastUserContent + "\n我已经结合近期记录给你分析建议。";
    }

    @Override
    public String chat(String conversationId, String systemPrompt, List<ChatEntry> history, List<ToolResult> toolResults) {
        String lastUserContent = history.stream()
                .filter(e -> "user".equals(e.role()))
                .reduce((a, b) -> b)
                .map(ChatEntry::content)
                .orElse("");

        String toolText = toolResults.isEmpty()
                ? "本次没有读取额外数据。"
                : "我已读取 " + toolResults.stream().map(ToolResult::toolName).collect(Collectors.toList()) + " 的汇总数据。";

        boolean useLongTermMemory = systemPrompt != null && systemPrompt.contains("长期记忆");
        String memoryText = useLongTermMemory ? "已启用长期记忆。" : "本次未使用长期记忆。";
        if (userDataTools != null && EXPLICIT_PREFERENCE_PATTERN.matcher(lastUserContent).find()) {
            userDataTools.saveLongTermMemoryTool("preference", lastUserContent.strip());
            memoryText += " 已为你记录这条长期偏好。";
        }

        return "收到：" + lastUserContent.strip() + "\n" + toolText + "\n" + memoryText
                + "\n建议先从一个最小可执行动作开始，并在记录数据后继续让我结合趋势复盘。";
    }
}
