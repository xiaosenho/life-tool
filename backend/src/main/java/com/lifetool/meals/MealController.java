package com.lifetool.meals;

import java.time.LocalDate;
import java.util.List;

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
import com.lifetool.meals.dto.MealRecordResponse;

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

    @GetMapping
    public ResponseEntity<ApiResponse<List<MealRecordResponse>>> listMealsByDate(
            @AuthenticationPrincipal String userId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) LocalDate date,
            @org.springframework.web.bind.annotation.RequestParam(required = false) LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam(required = false) LocalDate to) {
        if (date != null) {
            return ResponseEntity.ok(ApiResponse.ok(mealService.listMealsByDate(userId, date)));
        }
        if (from != null && to != null) {
            return ResponseEntity.ok(ApiResponse.ok(mealService.listMealsByDateRange(userId, from, to)));
        }
        throw new MealException("VALIDATION_ERROR", "date or from/to is required");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MealDetailResponse>> getMeal(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(mealService.getMealDetail(userId, id)));
    }

    @GetMapping("/{id}/image-url")
    public ResponseEntity<ApiResponse<String>> getMealImageUrl(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(mealService.getMealImageUrl(userId, id)));
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
