package com.lifetool.ai;

import com.lifetool.ai.dto.FoodRecognitionResponse;
import com.lifetool.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/food-recognition")
public class FoodRecognitionController {

    private final FoodRecognitionService foodRecognitionService;

    public FoodRecognitionController(FoodRecognitionService foodRecognitionService) {
        this.foodRecognitionService = foodRecognitionService;
    }

    @PostMapping("/jobs")
    public ResponseEntity<ApiResponse<Map<String, String>>> createJob(
            @AuthenticationPrincipal String userId,
            @RequestBody Map<String, String> body) {
        String mediaAssetId = body.get("mediaAssetId");
        if (mediaAssetId == null || mediaAssetId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("VALIDATION_ERROR", "mediaAssetId is required"));
        }
        var job = foodRecognitionService.createJob(userId, mediaAssetId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("jobId", job.getId(), "status", "pending")));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<FoodRecognitionResponse>> getJob(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        FoodRecognitionResponse resp = foodRecognitionService.getJobResult(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }
}
