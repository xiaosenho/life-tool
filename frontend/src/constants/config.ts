const DEFAULT_API_BASE_URL = "http://47.96.129.185:8091/api";

function normalizeApiBaseUrl(url: string) {
  return url.replace(/\/+$/, "");
}

export const API_BASE_URL = normalizeApiBaseUrl(
  process.env.EXPO_PUBLIC_API_BASE_URL || DEFAULT_API_BASE_URL
);
