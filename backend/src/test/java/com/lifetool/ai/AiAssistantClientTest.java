package com.lifetool.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;

import com.lifetool.media.MediaService;

class AiAssistantClientTest {

    @Test
    void mockClientReturnsDeterministicReply() {
        MockAiAssistantClient mock = new MockAiAssistantClient();
        List<AiAssistantClient.ChatEntry> history = List.of(
                new AiAssistantClient.ChatEntry("user", "帮我看看专注数据"));
        List<AiAssistantClient.ToolResult> toolResults = List.of(
                new AiAssistantClient.ToolResult("get_focus_summary", Map.of("domain", "focus")));

        String reply = mock.chat("test-conversation", "系统提示词", history, toolResults);

        assertNotNull(reply);
        assertTrue(reply.contains("收到：帮我看看专注数据"));
        assertTrue(reply.contains("get_focus_summary"));
        assertTrue(reply.contains("建议先从一个最小可执行动作开始"));
    }

    @Test
    void mockClientHandlesEmptyToolResults() {
        MockAiAssistantClient mock = new MockAiAssistantClient();
        List<AiAssistantClient.ChatEntry> history = List.of(
                new AiAssistantClient.ChatEntry("user", "你好"));

        String reply = mock.chat("test-conversation", "系统提示词", history, List.of());

        assertTrue(reply.contains("本次没有读取额外数据"));
    }

    @Test
    void mockClientDetectsLongTermMemoryFromSystemPrompt() {
        MockAiAssistantClient mock = new MockAiAssistantClient();
        List<AiAssistantClient.ChatEntry> history = List.of(
                new AiAssistantClient.ChatEntry("user", "你好"));

        String withMemory = mock.chat("test-conversation", "已启用长期记忆。", history, List.of());
        assertTrue(withMemory.contains("已启用长期记忆"));

        String withoutMemory = mock.chat("test-conversation", "普通提示词", history, List.of());
        assertTrue(withoutMemory.contains("本次未使用长期记忆"));
    }

    @Test
    void springAiClientCanBeInstantiated() {
        // Verify SpringAiAssistantClient implements the interface
        assertTrue(AiAssistantClient.class.isAssignableFrom(SpringAiAssistantClient.class));
    }

    @Test
    void springAiClientReadsOwnedAudioBytesDirectly() throws Exception {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, Answers.RETURNS_SELF);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        MediaService mediaService = mock(MediaService.class);
        when(mediaService.readAssetBytes("u1", "asset-audio", "chat_audio"))
                .thenReturn("hello-audio".getBytes(StandardCharsets.UTF_8));

        SpringAiAssistantClient client = new SpringAiAssistantClient(
                builder,
                mock(UserDataTools.class),
                mediaService,
                "test-api-key",
                "https://example.com",
                "test-model",
                "/v1/chat/completions");

        Method method = SpringAiAssistantClient.class.getDeclaredMethod(
                "fetchAudioAsBase64WithRetry",
                AiAssistantClient.MediaInput.class);
        method.setAccessible(true);

        String base64 = (String) method.invoke(
                client,
                new AiAssistantClient.MediaInput(
                        "audio",
                        "https://cos.example.com/expired.mp3",
                        "audio/mp3",
                        "asset-audio",
                        "u1"));

        assertEquals(Base64.getEncoder().encodeToString("hello-audio".getBytes(StandardCharsets.UTF_8)), base64);
        verify(mediaService).readAssetBytes("u1", "asset-audio", "chat_audio");
    }

    @Test
    void toolAnnotationsPresent() throws Exception {
        var methods = UserDataTools.class.getDeclaredMethods();
        List<String> toolMethods = new java.util.ArrayList<>();
        for (var method : methods) {
            if (method.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class)) {
                toolMethods.add(method.getName());
            }
        }
        assertEquals(7, toolMethods.size(), "Expected 7 @Tool annotated methods");
        assertTrue(toolMethods.contains("getFocusSummaryTool"));
        assertTrue(toolMethods.contains("getHabitSummaryTool"));
        assertTrue(toolMethods.contains("getDietSummaryTool"));
        assertTrue(toolMethods.contains("getLedgerSummaryTool"));
        assertTrue(toolMethods.contains("getUpcomingEventsTool"));
        assertTrue(toolMethods.contains("getUserProfileContextTool"));
        assertTrue(toolMethods.contains("saveLongTermMemoryTool"));
    }
}
