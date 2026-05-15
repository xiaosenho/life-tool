package com.lifetool.meals;

public interface MealStore {
    MealLog saveAiMealLog(MealLog mealLog);

    MealLog updateAiMealLog(MealLog mealLog);

    MealSummary getSummary(String userId);

    MealLog findById(String userId, String mealLogId);

    void delete(String userId, String mealLogId);
}
