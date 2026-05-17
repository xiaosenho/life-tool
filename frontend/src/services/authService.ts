import { apiClient } from "./apiClient";

export interface User {
  id: string;
  email: string;
  displayName: string;
  avatarAssetId?: string | null;
  avatarUrl?: string | null;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

const MOCK_ENABLED = process.env.EXPO_PUBLIC_AUTH_MOCK_ENABLED === "true";

export const authService = {
  async login(email: string, password: string): Promise<AuthResponse> {
    if (MOCK_ENABLED) {
      await new Promise((resolve) => setTimeout(resolve, 1000));
      if (email === "test@example.com" && password === "password") {
        return {
          accessToken: "mock_access_token",
          refreshToken: "mock_refresh_token",
          user: {
            id: "1",
            email: "test@example.com",
            displayName: "测试用户",
          },
        };
      }
      throw new Error("邮箱或密码错误");
    }

    const response = await apiClient.post<AuthResponse>("/auth/login", {
      email,
      password,
    });

    if (!response.success || !response.data) {
      throw new Error(response.error?.message || "登录失败");
    }

    return response.data;
  },

  async register(
    email: string,
    password: string,
    displayName: string
  ): Promise<AuthResponse> {
    if (MOCK_ENABLED) {
      await new Promise((resolve) => setTimeout(resolve, 1000));
      return {
        accessToken: "mock_access_token",
        refreshToken: "mock_refresh_token",
        user: {
          id: Math.random().toString(36).substr(2, 9),
          email,
          displayName,
        },
      };
    }

    const response = await apiClient.post<AuthResponse>("/auth/register", {
      email,
      password,
      displayName,
    });

    if (!response.success || !response.data) {
      throw new Error(response.error?.message || "注册失败");
    }

    return response.data;
  },

  async logout(): Promise<void> {
    if (MOCK_ENABLED) {
      return;
    }
    const refreshToken = await import("@/store/authStore")
      .then(({ useAuthStore }) => useAuthStore.getState().refreshToken);
    await apiClient.post("/auth/logout", refreshToken ? { refreshToken } : undefined);
  },

  async refresh(refreshToken: string): Promise<AuthResponse> {
    if (MOCK_ENABLED) {
      return {
        accessToken: "mock_access_token",
        refreshToken: "mock_refresh_token",
        user: {
          id: "1",
          email: "test@example.com",
          displayName: "测试用户",
        },
      };
    }

    const response = await apiClient.post<AuthResponse>("/auth/refresh", {
      refreshToken,
    });

    if (!response.success || !response.data) {
      const error = new Error(response.error?.message || "登录状态已过期") as Error & { code?: string };
      error.code = response.error?.code;
      throw error;
    }

    return response.data;
  },

  async getMe(): Promise<User> {
    if (MOCK_ENABLED) {
      return {
        id: "1",
        email: "test@example.com",
        displayName: "测试用户",
      };
    }
    const response = await apiClient.get<User>("/me");
    if (!response.success || !response.data) {
      throw new Error(response.error?.message || "获取用户信息失败");
    }
    return response.data;
  },

  async updateProfile(input: { displayName?: string; avatarAssetId?: string | null }): Promise<User> {
    if (MOCK_ENABLED) {
      const current = await import("@/store/authStore")
        .then(({ useAuthStore }) => useAuthStore.getState().user);
      return {
        id: current?.id ?? "1",
        email: current?.email ?? "test@example.com",
        displayName: input.displayName ?? current?.displayName ?? "测试用户",
        avatarAssetId: input.avatarAssetId ?? current?.avatarAssetId ?? null,
        avatarUrl: current?.avatarUrl ?? null,
      };
    }
    const response = await apiClient.patch<User>("/me/profile", input);
    if (!response.success || !response.data) {
      throw new Error(response.error?.message || "资料更新失败");
    }
    return response.data;
  },

  async changePassword(input: { currentPassword: string; newPassword: string }): Promise<void> {
    if (MOCK_ENABLED) {
      return;
    }
    const response = await apiClient.post<void>("/me/password", input);
    if (!response.success) {
      throw new Error(response.error?.message || "密码修改失败");
    }
  },
};
