package com.lifetool.meals;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lifetool.common.ApiResponse;
import com.lifetool.meals.dto.MealDetailResponse;

@RestController
@RequestMapping("/api/meals")
public class MealController {
    private final MealService mealService;

    public MealController(MealService mealService) {
        this.mealService = mealService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<MealSummary>> getSummary(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(mealService.getSummary(userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MealDetailResponse>> getMeal(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(mealService.getMealDetail(userId, id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMeal(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        mealService.deleteMeal(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/rerun-recognition")
    public ResponseEntity<ApiResponse<MealDetailResponse>> rerunRecognition(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(mealService.rerunRecognition(userId, id)));
    }
}
