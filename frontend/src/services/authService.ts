import { apiClient } from "./apiClient";

export interface User {
  id: string;
  email: string;
  displayName: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

const MOCK_ENABLED = true;

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
            displayName: "Test User",
          },
        };
      }
      throw new Error("Invalid email or password");
    }

    const response = await apiClient.post<AuthResponse>("/auth/login", {
      email,
      password,
    });

    if (!response.success || !response.data) {
      throw new Error(response.error?.message || "Login failed");
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
      throw new Error(response.error?.message || "Registration failed");
    }

    return response.data;
  },

  async logout(): Promise<void> {
    if (MOCK_ENABLED) {
      return;
    }
    await apiClient.post("/auth/logout");
  },

  async getMe(): Promise<User> {
    if (MOCK_ENABLED) {
      return {
        id: "1",
        email: "test@example.com",
        displayName: "Test User",
      };
    }
    const response = await apiClient.get<User>("/me");
    if (!response.success || !response.data) {
      throw new Error(response.error?.message || "Failed to fetch user");
    }
    return response.data;
  },
};
