package com.lifetool.ai.dto;

import java.util.List;

public record FoodRecognitionResponse(
        String jobId,
        String status,
        FoodResult result
) {
    public record FoodResult(
            List<FoodItem> items,
            int totalCalories,
            String notes
    ) {}

    public record FoodItem(
            String name,
            int estimatedGrams,
            int estimatedCalories,
            double confidence
    ) {}
}
