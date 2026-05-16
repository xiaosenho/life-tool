package com.lifetool.ai.dto;

import java.math.BigDecimal;

public record FoodRecognitionResponse(
        String result,
        String disclaimer,
        String mealLogId,
        BigDecimal totalCalories
) {
}
