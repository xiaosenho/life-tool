package com.lifetool.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class DoubaoMockClient {

    private static final Logger log = LoggerFactory.getLogger(DoubaoMockClient.class);
    private final ObjectMapper mapper;

    public DoubaoMockClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public CompletableFuture<JsonNode> chatWithVision(String systemPrompt, String userMessage, String imageUrl) {
        log.info("Mock 豆包 vision: prompt={}, imageUrl={}", systemPrompt, imageUrl);
        return CompletableFuture.completedFuture(buildResponse(mockFoodResult()));
    }

    public CompletableFuture<JsonNode> chat(String systemPrompt, String userMessage) {
        log.info("Mock 豆包 chat: prompt={}, message={}", systemPrompt, userMessage);
        return CompletableFuture.completedFuture(buildResponse(mockLifeAdvice()));
    }

    public CompletableFuture<JsonNode> chatWithHistory(String systemPrompt, List<JsonNode> history) {
        log.info("Mock 豆包 chat with history: {} messages", history.size());
        return CompletableFuture.completedFuture(buildResponse("这是模拟的 AI 回复。在实际对接豆包后，我会根据你的数据给出更有用的建议。"));
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
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private JsonNode buildResponse(String text) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode output = root.putArray("output").addObject();
        output.put("type", "message");
        output.put("role", "assistant");
        output.putArray("content").addObject().put("type", "output_text").put("text", text);
        return root;
    }

    private String mockFoodResult() {
        return """
                {"items":[{"name":"米饭","estimatedGrams":150,"estimatedCalories":174,"confidence":0.85},{"name":"番茄炒蛋","estimatedGrams":200,"estimatedCalories":220,"confidence":0.72},{"name":"清炒西兰花","estimatedGrams":120,"estimatedCalories":42,"confidence":0.68}],"totalCalories":436,"notes":"识别结果为估算值，请确认后保存。"}""";
    }

    private String mockLifeAdvice() {
        return """
                {"summary":"你最近 7 天专注时间较稳定，平均每天约 45 分钟，习惯完成率 60%。","suggestions":["把高强度专注安排在上午，效率更高。","晚餐可以适当减少碳水摄入。","连续打卡已坚持 7 天，继续保持！"],"disclaimer":"AI 建议仅供参考，不构成医疗或营养诊断。"}""";
    }
}
