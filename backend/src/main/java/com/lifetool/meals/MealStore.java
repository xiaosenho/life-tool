package com.lifetool.meals;

import java.time.LocalDate;
import java.util.List;

public interface MealStore {
    MealLog saveAiMealLog(MealLog mealLog);

    MealLog updateAiMealLog(MealLog mealLog);

    MealSummary getSummary(String userId);

    MealLog findById(String userId, String mealLogId);

    List<MealLog> findByUserIdAndDate(String userId, LocalDate date);

    List<MealLog> findByUserIdAndDateRange(String userId, LocalDate from, LocalDate to);

    void delete(String userId, String mealLogId);
}
