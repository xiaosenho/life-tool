package com.lifetool.meals;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lifetool.common.ApiResponse;

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
}
