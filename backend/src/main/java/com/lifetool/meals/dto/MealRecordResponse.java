package com.lifetool.meals.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.lifetool.meals.MealLog;

public record MealRecordResponse(
        String id,
        String mealType,
        Instant occurredAt,
        BigDecimal totalCalories,
        boolean aiGenerated
) {
    public static MealRecordResponse from(MealLog mealLog) {
        return new MealRecordResponse(
                mealLog.getId(),
                mealLog.getMealType(),
                mealLog.getOccurredAt(),
                mealLog.getTotalCalories(),
                mealLog.isAiGenerated());
    }
}
