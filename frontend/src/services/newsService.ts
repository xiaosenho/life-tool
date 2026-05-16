import { apiClient } from "./apiClient";

export interface NewsItem {
  title: string;
  source: string;
  url: string;
  publishedAt: string;
  summary: string;
  imageUrl?: string | null;
}

export const newsService = {
  getTopNews() {
    return apiClient.get<NewsItem[]>("/news/top");
  },
};
