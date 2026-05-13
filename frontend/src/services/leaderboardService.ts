import { apiClient } from "./apiClient";

export interface LeaderboardEntry {
  userId: string;
  displayName: string;
  avatarUrl: string | null;
  value: number;
  rank: number;
}

export interface LeaderboardResponse {
  period: string;
  metric: string;
  entries: LeaderboardEntry[];
}

const MOCK_ENABLED = true;

export const leaderboardService = {
  async getFocus(period: "today" | "week"): Promise<LeaderboardResponse> {
    if (MOCK_ENABLED) {
      return {
        period,
        metric: "focus_seconds",
        entries: [
          { userId: "me", displayName: "我", avatarUrl: null, value: period === "today" ? 3600 : 25200, rank: 1 },
          { userId: "friend1", displayName: "Alice", avatarUrl: null, value: period === "today" ? 2400 : 18000, rank: 2 },
          { userId: "friend2", displayName: "Bob", avatarUrl: null, value: period === "today" ? 1800 : 14400, rank: 3 },
        ],
      };
    }
    const resp = await apiClient.get<LeaderboardResponse>(`/leaderboards/focus?period=${period}`);
    return resp.data ?? { period, metric: "focus_seconds", entries: [] };
  },

  async getHabits(): Promise<LeaderboardResponse> {
    if (MOCK_ENABLED) {
      return {
        period: "today",
        metric: "habit_completion",
        entries: [
          { userId: "friend1", displayName: "Alice", avatarUrl: null, value: 5, rank: 1 },
          { userId: "me", displayName: "我", avatarUrl: null, value: 3, rank: 2 },
          { userId: "friend2", displayName: "Bob", avatarUrl: null, value: 2, rank: 3 },
        ],
      };
    }
    const resp = await apiClient.get<LeaderboardResponse>("/leaderboards/habits?period=today");
    return resp.data ?? { period: "today", metric: "habit_completion", entries: [] };
  },

  async getStreaks(): Promise<LeaderboardResponse> {
    if (MOCK_ENABLED) {
      return {
        period: "all_time",
        metric: "streak_days",
        entries: [
          { userId: "friend1", displayName: "Alice", avatarUrl: null, value: 15, rank: 1 },
          { userId: "me", displayName: "我", avatarUrl: null, value: 7, rank: 2 },
          { userId: "friend2", displayName: "Bob", avatarUrl: null, value: 3, rank: 3 },
        ],
      };
    }
    const resp = await apiClient.get<LeaderboardResponse>("/leaderboards/streaks");
    return resp.data ?? { period: "all_time", metric: "streak_days", entries: [] };
  },
};
