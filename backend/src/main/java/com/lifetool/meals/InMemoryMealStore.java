package com.lifetool.meals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryMealStore implements MealStore {
    private final Map<String, MealLog> byId = new ConcurrentHashMap<>();

    @Override
    public MealLog saveAiMealLog(MealLog mealLog) {
        byId.put(mealLog.getId(), mealLog);
        return mealLog;
    }

    @Override
    public MealSummary getSummary(String userId) {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(6);
        List<MealLog> meals = byId.values().stream()
                .filter(m -> userId.equals(m.getUserId()))
                .sorted(Comparator.comparing(MealLog::getOccurredAt).reversed())
                .toList();

        List<MealLog> todayMeals = meals.stream()
                .filter(m -> today.equals(LocalDate.ofInstant(m.getOccurredAt(), ZoneId.systemDefault())))
                .toList();
        List<MealLog> weekMeals = meals.stream()
                .filter(m -> !LocalDate.ofInstant(m.getOccurredAt(), ZoneId.systemDefault()).isBefore(sevenDaysAgo))
                .toList();

        return new MealSummary(
                sumCalories(todayMeals),
                todayMeals.size(),
                sumCalories(weekMeals),
                weekMeals.size(),
                meals.stream().limit(5).map(this::toRecentMeal).toList());
    }

    private MealSummary.RecentMeal toRecentMeal(MealLog meal) {
        return new MealSummary.RecentMeal(
                meal.getId(), meal.getMealType(), meal.getOccurredAt(), meal.getTotalCalories(), meal.isAiGenerated());
    }

    private static BigDecimal sumCalories(List<MealLog> meals) {
        return meals.stream()
                .map(MealLog::getTotalCalories)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
