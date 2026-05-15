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

export interface MealDetail {
  id: string;
  mealType: string;
  occurredAt: string;
  totalCalories: number | null;
  note: string | null;
  mediaAssetId: string | null;
  imageUrl: string | null;
  aiGenerated: boolean;
}

export const mealService = {
  getSummary() {
    return apiClient.get<MealSummary>("/meals/summary");
  },

  getMeal(id: string) {
    return apiClient.get<MealDetail>(`/meals/${id}`);
  },

  deleteMeal(id: string) {
    return apiClient.delete<void>(`/meals/${id}`);
  },

  rerunRecognition(id: string) {
    return apiClient.post<MealDetail>(`/meals/${id}/rerun-recognition`);
  }
};
