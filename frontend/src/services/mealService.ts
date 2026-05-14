import { apiClient } from "./apiClient";

export interface RecentMeal {
  id: string;
  mealType: string;
  occurredAt: string;
  totalCalories: number;
  aiGenerated: boolean;
}

export interface MealSummary {
  todayTotalCalories: number;
  todayMealCount: number;
  last7DaysTotalCalories: number;
  last7DaysMealCount: number;
  recentMeals: RecentMeal[];
}

export const mealService = {
  getSummary() {
    return apiClient.get<MealSummary>("/meals/summary");
  },
};
