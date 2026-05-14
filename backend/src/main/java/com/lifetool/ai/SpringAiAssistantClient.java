package com.lifetool.ai;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

public class SpringAiAssistantClient implements AiAssistantClient {

    private final ChatClient chatClient;
    private final UserDataTools userDataTools;

    public SpringAiAssistantClient(ChatClient.Builder chatClientBuilder, UserDataTools userDataTools) {
        var chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.userDataTools = userDataTools;
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
}
