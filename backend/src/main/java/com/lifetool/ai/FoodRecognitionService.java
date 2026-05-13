package com.lifetool.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifetool.ai.dto.AiJob;
import com.lifetool.ai.dto.FoodRecognitionResponse;
import com.lifetool.ai.store.AiJobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class FoodRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(FoodRecognitionService.class);
    private static final String SYSTEM_PROMPT = """
            你是一个专业的饮食识别助手。请分析图片中的食物，返回 JSON 格式的结果。
            注意：
            1. 识别每种食物的名称（中文）
            2. 估算每种食物的重量（克）
            3. 估算每种食物的热量（千卡）
            4. 给出置信度（0-1）
            5. 计算总热量
            返回格式必须是以下 JSON，不要包含其他文字：
            {"items":[{"name":"...","estimatedGrams":100,"estimatedCalories":100,"confidence":0.8}],"totalCalories":100,"notes":"..."}
            """;

    private final AiConfig config;
    private final DoubaoClient doubaoClient;
    private final DoubaoMockClient mockClient;
    private final AiJobStore jobStore;
    private final ObjectMapper mapper;

    public FoodRecognitionService(AiConfig config, DoubaoClient doubaoClient,
                                  DoubaoMockClient mockClient, AiJobStore jobStore, ObjectMapper mapper) {
        this.config = config;
        this.doubaoClient = doubaoClient;
        this.mockClient = mockClient;
        this.jobStore = jobStore;
        this.mapper = mapper;
    }

    public AiJob createJob(String userId, String mediaAssetId) {
        AiJob job = new AiJob(userId, "food_recognition", mediaAssetId);
        jobStore.save(job);

        String imageUrl = "https://cos.example.com/" + mediaAssetId;

        CompletableFuture<JsonNode> future;
        if (config.isMockEnabled()) {
            future = mockClient.chatWithVision(SYSTEM_PROMPT,
                    "请识别这张图片中的食物并估算热量。", imageUrl);
        } else {
            future = doubaoClient.chatWithVision(SYSTEM_PROMPT,
                    "请识别这张图片中的食物并估算热量。", imageUrl);
        }

        future.thenAccept(response -> {
            try {
                String content = doubaoClient.extractTextContent(response);
                jobStore.updateStatus(job.getId(), AiJob.Status.SUCCEEDED, content);
                log.info("Food recognition job {} completed", job.getId());
            } catch (Exception e) {
                log.error("Food recognition job {} failed", job.getId(), e);
                jobStore.updateStatus(job.getId(), AiJob.Status.FAILED, null);
                AiJob stored = jobStore.findById(job.getId()).orElse(null);
                if (stored != null) stored.setErrorMessage(e.getMessage());
            }
        }).exceptionally(e -> {
            log.error("Food recognition job {} failed", job.getId(), e);
            jobStore.updateStatus(job.getId(), AiJob.Status.FAILED, null);
            AiJob stored = jobStore.findById(job.getId()).orElse(null);
            if (stored != null) stored.setErrorMessage(e.getMessage());
            return null;
        });

        return job;
    }

    public FoodRecognitionResponse getJobResult(String userId, String jobId) {
        AiJob job = jobStore.findById(jobId)
                .orElseThrow(() -> new AiException("NOT_FOUND", "Job not found"));

        if (!job.getUserId().equals(userId)) {
            throw new AiException("FORBIDDEN", "Access denied");
        }

        FoodRecognitionResponse.FoodResult result = null;
        if (job.getResultJson() != null && AiJob.Status.SUCCEEDED.equals(job.getStatus())) {
            try {
                result = mapper.readValue(job.getResultJson(), FoodRecognitionResponse.FoodResult.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse job result: {}", job.getResultJson(), e);
            }
        }

        return new FoodRecognitionResponse(
                job.getId(),
                job.getStatus().name().toLowerCase(),
                result
        );
    }
}
