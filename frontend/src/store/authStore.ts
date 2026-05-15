import { create } from "zustand";
import { User } from "@/services/authService";
import { authStorage } from "@/services/authStorage";

interface AuthState {
  user: User | null;
  token: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  setAuth: (user: User, token: string, refreshToken: string) => void;
  restoreAuth: (user: User, token: string, refreshToken: string) => void;
  clearAuth: () => void;
  setLoading: (isLoading: boolean) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  refreshToken: null,
  isAuthenticated: false,
  isLoading: true,
  setAuth: (user, token, refreshToken) => {
    set({ user, token, refreshToken, isAuthenticated: true, isLoading: false });
    authStorage.save({ user, accessToken: token, refreshToken }).catch(console.error);
  },
  restoreAuth: (user, token, refreshToken) => {
    set({ user, token, refreshToken, isAuthenticated: true, isLoading: false });
  },
  clearAuth: () => {
    set({ user: null, token: null, refreshToken: null, isAuthenticated: false, isLoading: false });
    authStorage.clear().catch(console.error);
  },
  setLoading: (isLoading) => set({ isLoading }),
}));
