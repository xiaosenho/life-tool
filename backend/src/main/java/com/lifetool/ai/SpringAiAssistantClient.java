package com.lifetool.ai;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;

public class SpringAiAssistantClient implements AiAssistantClient {
    private static final Logger log = LoggerFactory.getLogger(SpringAiAssistantClient.class);

    private final ChatClient chatClient;
    private final ChatClient statelessChatClient;
    private final UserDataTools userDataTools;
    private final RestClient restClient;
    private final String chatModel;

    public SpringAiAssistantClient(
            ChatClient.Builder chatClientBuilder,
            UserDataTools userDataTools,
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl,
            @Value("${spring.ai.openai.chat.options.model}") String chatModel) {
        var chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.statelessChatClient = chatClientBuilder.build();
        this.userDataTools = userDataTools;
        this.chatModel = chatModel;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String chatWithImage(String conversationId, String systemPrompt, String imageUrl, String userText) {
        log.info(
                "Calling AI chatWithImage via direct REST API. conversationId={}, model={}, imagePath={}",
                conversationId,
                chatModel,
                summarizeUrl(imageUrl));
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of(
                    "role", "system",
                    "content", systemPrompt));
        }
        messages.add(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "text", "text", userText != null ? userText : "请识别这张图片中的食物，并估算热量"),
                        Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)))));

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", chatModel,
                            "messages", messages))
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException ex) {
            log.error(
                    "AI chatWithImage direct REST call failed. conversationId={}, model={}, imagePath={}, message={}",
                    conversationId,
                    chatModel,
                    summarizeUrl(imageUrl),
                    ex.getMessage(),
                    ex);
            throw ex;
        }

        if (response == null) {
            throw new IllegalStateException("Empty AI response");
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("No AI choices returned");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        Object content = message == null ? null : message.get("content");
        log.info(
                "AI chatWithImage direct REST call succeeded. conversationId={}, model={}, choices={}, contentLength={}",
                conversationId,
                chatModel,
                choices.size(),
                content == null ? 0 : content.toString().length());
        return content == null ? "" : content.toString();
    }

    @Override
    public String chat(String conversationId, String systemPrompt, List<ChatEntry> history, List<ToolResult> toolResults) {
        StringBuilder fullSystem = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            fullSystem.append(systemPrompt);
        }
        if (!toolResults.isEmpty()) {
            fullSystem.append("\n\n以下是已查询到的用户数据工具结果：\n");
            for (ToolResult tr : toolResults) {
                fullSystem.append("- ").append(tr.toolName()).append(": ").append(tr.data()).append("\n");
            }
        }

        List<Message> messages = new java.util.ArrayList<>();
        if (!fullSystem.isEmpty()) {
            messages.add(new SystemMessage(fullSystem.toString()));
        }
        for (ChatEntry entry : history) {
            switch (entry.role()) {
                case "user" -> messages.add(new UserMessage(entry.content()));
                case "assistant" -> messages.add(new AssistantMessage(entry.content()));
            }
        }

        Prompt prompt = new Prompt(messages);
        return chatClient.prompt(prompt)
                .tools(userDataTools)
                .advisors(a -> a.param("chat_memory_conversation_id", conversationId))
                .call()
                .content();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String chatWithMedia(String conversationId, String systemPrompt, List<ChatEntry> history,
                                List<ToolResult> toolResults, MediaInput mediaInput) {
        StringBuilder system = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            system.append(systemPrompt);
        }
        if (!toolResults.isEmpty()) {
            system.append("\n\n以下是已查询到的用户数据工具结果：\n");
            for (ToolResult tr : toolResults) {
                system.append("- ").append(tr.toolName()).append(": ").append(tr.data()).append("\n");
            }
        }

        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        if (!system.isEmpty()) {
            messages.add(Map.of(
                    "role", "system",
                    "content", system.toString()));
        }

        for (ChatEntry entry : history) {
            if (!"user".equals(entry.role()) && !"assistant".equals(entry.role())) {
                continue;
            }
            if ("user".equals(entry.role()) && mediaInput != null && entry.equals(history.get(history.size() - 1))) {
                List<Map<String, Object>> content = new java.util.ArrayList<>();
                if (entry.content() != null && !entry.content().isBlank()) {
                    content.add(Map.of("type", "text", "text", entry.content()));
                }
                if ("audio".equals(mediaInput.kind())) {
                    content.add(Map.of(
                            "type", "input_audio",
                            "input_audio", Map.of(
                                    "data", fetchAudioAsBase64(mediaInput.url()),
                                    "format", audioFormat(mediaInput.contentType()))));
                } else {
                    content.add(Map.of(
                            "type", "image_url",
                            "image_url", Map.of("url", mediaInput.url())));
                }
                messages.add(Map.of("role", "user", "content", content));
            } else {
                messages.add(Map.of("role", entry.role(), "content", entry.content()));
            }
        }

        Map<String, Object> response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", chatModel,
                        "messages", messages))
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException("Empty AI response");
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("No AI choices returned");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        Object content = message == null ? null : message.get("content");
        return content == null ? "" : content.toString();
    }

    private String audioFormat(String contentType) {
        return switch (contentType) {
            case "audio/wav" -> "wav";
            case "audio/mpeg", "audio/mp3" -> "mp3";
            default -> "m4a";
        };
    }

    private String fetchAudioAsBase64(String url) {
        byte[] bytes = restClient.get()
                .uri(url)
                .retrieve()
                .body(byte[].class);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("Failed to load audio bytes");
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String summarizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "<empty>";
        }
        int queryIndex = url.indexOf('?');
        return queryIndex >= 0 ? url.substring(0, queryIndex) : url;
    }
}
