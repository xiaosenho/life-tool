package com.lifetool.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean
    @ConditionalOnProperty(name = "lifetool.ai.mock-enabled", havingValue = "true", matchIfMissing = true)
    public AiAssistantClient mockAiAssistantClient(UserDataTools userDataTools) {
        return new MockAiAssistantClient(userDataTools);
    }

    @Bean
    @ConditionalOnProperty(name = "lifetool.ai.mock-enabled", havingValue = "false")
    public AiAssistantClient springAiAssistantClient(ChatClient.Builder chatClientBuilder, UserDataTools userDataTools) {
        return new SpringAiAssistantClient(chatClientBuilder, userDataTools);
    }
}
