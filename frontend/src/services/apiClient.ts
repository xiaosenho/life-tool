import { API_BASE_URL } from "@/constants/config";
import { useAuthStore } from "@/store/authStore";

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: {
    code: string;
    message: string;
  } | null;
}

export const apiClient = {
  async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<ApiResponse<T>> {
    const url = `${API_BASE_URL}${endpoint}`;

    const token = useAuthStore.getState().token;
    
    const headers = {
      "Content-Type": "application/json",
      ...(token ? { "Authorization": `Bearer ${token}` } : {}),
      ...(options.headers || {}),
    };

    try {
      const response = await fetch(url, {
        ...options,
        headers,
      });

      const result = await response.json();

      if (!response.ok) {
        return {
          success: false,
          data: null,
          error: result.error || {
            code: "UNKNOWN_ERROR",
            message: "请求失败，请稍后再试",
          },
        };
      }

      return {
        success: true,
        data: result.data || result, // Handle both wrapped and unwrapped data
        error: null,
      };
    } catch (error) {
      return {
        success: false,
        data: null,
        error: {
          code: "NETWORK_ERROR",
          message: error instanceof Error ? error.message : "网络异常，请检查连接",
        },
      };
    }
  },

  get<T>(endpoint: string, options: RequestInit = {}) {
    return this.request<T>(endpoint, { ...options, method: "GET" });
  },

  post<T>(endpoint: string, body?: any, options: RequestInit = {}) {
    return this.request<T>(endpoint, {
      ...options,
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
    });
  },

  patch<T>(endpoint: string, body?: any, options: RequestInit = {}) {
    return this.request<T>(endpoint, {
      ...options,
      method: "PATCH",
      body: body ? JSON.stringify(body) : undefined,
    });
  },

  delete<T>(endpoint: string, options: RequestInit = {}) {
    return this.request<T>(endpoint, { ...options, method: "DELETE" });
  },
};
