import { create } from "zustand";
import { newsService, NewsItem } from "@/services/newsService";
import { useAuthStore } from "@/store/authStore";

type BootstrapStatus = "idle" | "loading" | "success" | "error";

interface NewsBootstrapState {
  status: BootstrapStatus;
  data: NewsItem[];
  consumed: boolean;
  prefetch: () => void;
  consume: () => NewsItem[] | null;
  reset: () => void;
}

let prefetchPromise: Promise<void> | null = null;

export const useNewsBootstrapStore = create<NewsBootstrapState>((set, get) => ({
  status: "idle",
  data: [],
  consumed: false,

  prefetch: () => {
    const { status } = get();
    // 去重：已在 loading/success/error 状态时不重复触发
    if (status !== "idle") return;

    // 检查登录态
    if (!useAuthStore.getState().isAuthenticated) return;

    set({ status: "loading" });

    // 用共享 promise 防止 Strict Mode 双调用
    if (!prefetchPromise) {
      prefetchPromise = newsService
        .getTopNews()
        .then((response) => {
          prefetchPromise = null;
          if (response.success && response.data) {
            set({ status: "success", data: response.data });
          } else {
            set({ status: "error", data: [] });
          }
        })
        .catch(() => {
          prefetchPromise = null;
          set({ status: "error", data: [] });
        });
    }
  },

  consume: () => {
    const { status, data, consumed } = get();
    if (consumed) return null;
    if (status === "success") {
      set({ consumed: true });
      return data;
    }
    // loading 时标记为即将消费，让 index.tsx 知道预取还在进行中
    if (status === "loading") {
      return null;
    }
    return null;
  },

  reset: () => {
    prefetchPromise = null;
    set({ status: "idle", data: [], consumed: false });
  },
}));
