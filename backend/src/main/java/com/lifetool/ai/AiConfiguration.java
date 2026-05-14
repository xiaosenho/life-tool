package com.lifetool.ai;

import java.io.IOException;
import java.net.URI;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean
    @ConditionalOnProperty(name = "lifetool.ai.mock-enabled", havingValue = "true", matchIfMissing = true)
    public AiAssistantClient mockAiAssistantClient() {
        return new MockAiAssistantClient();
    }

    @Bean
    @ConditionalOnProperty(name = "lifetool.ai.mock-enabled", havingValue = "false")
    public AiAssistantClient springAiAssistantClient(ChatClient.Builder chatClientBuilder, UserDataTools userDataTools) {
        return new SpringAiAssistantClient(chatClientBuilder, userDataTools);
    }

    @Bean
    @ConditionalOnProperty(name = "lifetool.ai.mock-enabled", havingValue = "false")
    public RestClientCustomizer doubaoUrlFixer() {
        return builder -> builder.requestInterceptor(new ClientHttpRequestInterceptor() {
            @Override
            public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                                  ClientHttpRequestExecution execution) throws IOException {
                String url = request.getURI().toString();
                if (url.contains("/api/v3/v1/")) {
                    String fixed = url.replace("/api/v3/v1/", "/api/v3/");
                    HttpRequestWrapper wrapper = new HttpRequestWrapper(request);
                    return execution.execute(new HttpRequest() {
                        @Override
                        public java.net.URI getURI() { return URI.create(fixed); }
                        @Override
                        public org.springframework.http.HttpMethod getMethod() { return request.getMethod(); }
                        @Override
                        public org.springframework.http.HttpHeaders getHeaders() { return request.getHeaders(); }
                    }, body);
                }
                return execution.execute(request, body);
            }
        });
    }
}
