package com.lifetool.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifetool.ai.dto.LifeAdviceRequest;
import com.lifetool.ai.dto.LifeAdviceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class LifeAdviceService {

    private static final Logger log = LoggerFactory.getLogger(LifeAdviceService.class);
    private static final String SYSTEM_PROMPT = """
            你是一个生活助手。根据用户提供的近期生活数据，给出简短的生活建议。
            要求：
            1. 先总结用户近期的状态
            2. 给出 2-3 条具体可操作的建议
            3. 最后加上免责声明
            返回 JSON 格式：{"summary":"...","suggestions":["...","..."],"disclaimer":"AI 建议仅供参考，不构成医疗或营养诊断。"}
            """;

    private final AiConfig config;
    private final DoubaoClient doubaoClient;
    private final DoubaoMockClient mockClient;
    private final ObjectMapper mapper;

    public LifeAdviceService(AiConfig config, DoubaoClient doubaoClient,
                             DoubaoMockClient mockClient, ObjectMapper mapper) {
        this.config = config;
        this.doubaoClient = doubaoClient;
        this.mockClient = mockClient;
        this.mapper = mapper;
    }

    public LifeAdviceResponse getAdvice(String userId, LifeAdviceRequest req) {
        String userDataSummary = "用户ID: " + userId + "\n请求周期: " + req.period()
                + "\n请求主题: " + req.topics()
                + "\n（当前为模拟数据：今日专注 60 分钟，习惯完成 3/5，连续打卡 7 天）"
                + "\n请根据这些信息给出生活建议。";

        CompletableFuture<JsonNode> future;
        if (config.isMockEnabled()) {
            future = mockClient.chat(SYSTEM_PROMPT, userDataSummary);
        } else {
            future = doubaoClient.chat(SYSTEM_PROMPT, userDataSummary);
        }

        try {
            JsonNode response = future.get();
            String resultJson = doubaoClient.extractTextContent(response);
            return mapper.readValue(resultJson, LifeAdviceResponse.class);
        } catch (Exception e) {
            log.warn("Failed to get life advice, using fallback", e);
            return new LifeAdviceResponse(
                    "暂时无法获取建议，请稍后再试。",
                    List.of("保持规律作息", "注意饮食均衡"),
                    "AI 建议仅供参考，不构成医疗或营养诊断。"
            );
        }
    }
}
