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

export interface LeaderboardDetailResponse extends LeaderboardResponse {
  self: LeaderboardEntry;
  gapToPrevious: number;
  totalParticipants: number;
}

export const leaderboardService = {
  getFocus(period: "today" | "week") {
    return apiClient.get<LeaderboardResponse>(`/leaderboards/focus?period=${period}`);
  },

  getFocusDetail(period: "today" | "week") {
    return apiClient.get<LeaderboardDetailResponse>(`/leaderboards/focus/detail?period=${period}`);
  },

  getHabitsToday() {
    return apiClient.get<LeaderboardResponse>("/leaderboards/habits?period=today");
  },

  getHabitsTodayDetail() {
    return apiClient.get<LeaderboardDetailResponse>("/leaderboards/habits/detail?period=today");
  },

  getStreaks() {
    return apiClient.get<LeaderboardResponse>("/leaderboards/streaks");
  },

  getStreaksDetail() {
    return apiClient.get<LeaderboardDetailResponse>("/leaderboards/streaks/detail");
  }
};
