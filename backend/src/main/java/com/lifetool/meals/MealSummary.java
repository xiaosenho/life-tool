package com.lifetool.meals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MealSummary(
        BigDecimal todayTotalCalories,
        int todayMealCount,
        BigDecimal last7DaysTotalCalories,
        int last7DaysMealCount,
        List<RecentMeal> recentMeals) {

    public record RecentMeal(
            String id,
            String mealType,
            Instant occurredAt,
            BigDecimal totalCalories,
            boolean aiGenerated) {
    }
}
