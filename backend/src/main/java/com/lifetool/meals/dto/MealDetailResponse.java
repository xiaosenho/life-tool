package com.lifetool.meals.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MealDetailResponse(
        String id,
        String mealType,
        Instant occurredAt,
        BigDecimal totalCalories,
        String note,
        String mediaAssetId,
        String imageUrl,
        boolean aiGenerated
) {
}
