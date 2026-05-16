import { apiClient } from "./apiClient";

export interface RecentMeal {
  id: string;
  mealType: string;
  occurredAt: string;
  totalCalories: number;
  aiGenerated: boolean;
}

export interface MealRecord {
  id: string;
  mealType: string;
  occurredAt: string;
  totalCalories: number | null;
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

  listMealsByDate(date: string) {
    return apiClient.get<MealRecord[]>(`/meals?date=${encodeURIComponent(date)}`);
  },

  listMealsByRange(from: string, to: string) {
    return apiClient.get<MealRecord[]>(`/meals?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
  },

  getMeal(id: string) {
    return apiClient.get<MealDetail>(`/meals/${id}`);
  },

  getMealImageUrl(id: string) {
    return apiClient.get<string>(`/meals/${id}/image-url`);
  },

  deleteMeal(id: string) {
    return apiClient.delete<void>(`/meals/${id}`);
  },

  rerunRecognition(id: string) {
    return apiClient.post<MealDetail>(`/meals/${id}/rerun-recognition`);
  }
};
