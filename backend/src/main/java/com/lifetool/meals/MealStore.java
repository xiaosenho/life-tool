package com.lifetool.meals;

public interface MealStore {
    MealLog saveAiMealLog(MealLog mealLog);

    MealSummary getSummary(String userId);
}
