import { apiClient } from "./apiClient";

export interface LeaderboardEntry {
  userId: string;
  displayName: string;
  avatarUrl?: string | null;
  value: number;
  rank: number;
}

export interface LeaderboardResponse {
  period: string;
  metric: string;
  entries: LeaderboardEntry[];
}

export const leaderboardService = {
  getFocus(period: "today" | "week") {
    return apiClient.get<LeaderboardResponse>(`/leaderboards/focus?period=${period}`);
  },

  getHabitsToday() {
    return apiClient.get<LeaderboardResponse>("/leaderboards/habits?period=today");
  },

  getStreaks() {
    return apiClient.get<LeaderboardResponse>("/leaderboards/streaks");
  }
};
