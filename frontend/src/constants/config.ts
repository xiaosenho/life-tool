const DEFAULT_API_BASE_URL = "";

function normalizeApiBaseUrl(url: string) {
  return url.replace(/\/+$/, "");
}

export const API_BASE_URL = normalizeApiBaseUrl(
  process.env.EXPO_PUBLIC_API_BASE_URL || DEFAULT_API_BASE_URL
);
