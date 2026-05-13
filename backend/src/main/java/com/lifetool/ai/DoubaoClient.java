package com.lifetool.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class DoubaoClient {

    private static final Logger log = LoggerFactory.getLogger(DoubaoClient.class);

    private final AiConfig config;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public DoubaoClient(AiConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public CompletableFuture<JsonNode> chatWithVision(String systemPrompt, String userMessage, String imageUrl) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getDoubao().getVisionModel());

        ArrayNode input = body.putArray("input");

        ObjectNode systemMsg = input.addObject();
        systemMsg.put("role", "system");
        systemMsg.putArray("content").addObject().put("type", "input_text").put("text", systemPrompt);

        ObjectNode userMsg = input.addObject();
        userMsg.put("role", "user");
        ArrayNode content = userMsg.putArray("content");
        ObjectNode textPart = content.addObject();
        textPart.put("type", "input_text");
        textPart.put("text", userMessage);
        if (imageUrl != null) {
            ObjectNode imagePart = content.addObject();
            imagePart.put("type", "input_image");
            imagePart.put("image_url", imageUrl);
        }

        return sendRequest(body);
    }

    public CompletableFuture<JsonNode> chat(String systemPrompt, String userMessage) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getDoubao().getChatModel());
        

        ArrayNode input = body.putArray("input");

        ObjectNode systemMsg = input.addObject();
        systemMsg.put("role", "system");
        systemMsg.putArray("content").addObject().put("type", "input_text").put("text", systemPrompt);

        ObjectNode userMsg = input.addObject();
        userMsg.put("role", "user");
        userMsg.putArray("content").addObject().put("type", "input_text").put("text", userMessage);

        return sendRequest(body);
    }

    public CompletableFuture<JsonNode> chatWithHistory(String systemPrompt, List<JsonNode> history) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.getDoubao().getChatModel());
        

        ArrayNode input = body.putArray("input");

        ObjectNode systemMsg = input.addObject();
        systemMsg.put("role", "system");
        systemMsg.putArray("content").addObject().put("type", "input_text").put("text", systemPrompt);

        for (JsonNode msg : history) {
            ObjectNode entry = input.addObject();
            entry.put("role", msg.get("role").asText());
            String content = msg.get("content").asText();
            entry.putArray("content").addObject().put("type", "input_text").put("text", content);
        }

        return sendRequest(body);
    }

    private CompletableFuture<JsonNode> sendRequest(ObjectNode body) {
        String url = config.getDoubao().getBaseUrl() + "/responses";
        try {
            String json = mapper.writeValueAsString(body);
            log.debug("Doubao request: {}", json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getDoubao().getApiKey())
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            if (response.statusCode() != 200) {
                                log.error("Doubao API error: {} {}", response.statusCode(), response.body());
                                throw new RuntimeException("豆包 API 返回错误: " + response.statusCode());
                            }
                            JsonNode result = mapper.readTree(response.body());
                            log.debug("Doubao response: {}", result);
                            return result;
                        } catch (IOException e) {
                            throw new RuntimeException("解析豆包响应失败", e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("序列化请求失败", e);
        }
    }

    public String extractTextContent(JsonNode response) {
        try {
            JsonNode output = response.get("output");
            for (int i = 0; i < output.size(); i++) {
                JsonNode item = output.get(i);
                if ("message".equals(item.get("type").asText())) {
                    return item.get("content").get(0).get("text").asText();
                }
            }
            log.warn("No message output found in Doubao response: {}", response);
            return "";
        } catch (Exception e) {
            log.warn("Failed to extract content from Doubao response: {}", response);
            return "";
        }
    }
}
