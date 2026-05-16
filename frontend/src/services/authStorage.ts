import { Platform } from "react-native";
import { getDb } from "@/db/database";
import type { User } from "@/services/authService";

const STORAGE_KEY = "auth_session";

export interface PersistedAuthSession {
  user: User;
  accessToken: string;
  refreshToken: string;
}

export const authStorage = {
  async load(): Promise<PersistedAuthSession | null> {
    if (Platform.OS === "web" && typeof window !== "undefined") {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      return parseSession(raw);
    }

    const db = await getDb();
    const row = await db.getFirstAsync<{ value: string }>(
      "SELECT value FROM sync_state WHERE key = ?",
      [STORAGE_KEY]
    );
    return parseSession(row?.value ?? null);
  },

  async save(session: PersistedAuthSession): Promise<void> {
    const raw = JSON.stringify(session);
    if (Platform.OS === "web" && typeof window !== "undefined") {
      window.localStorage.setItem(STORAGE_KEY, raw);
      return;
    }

    const db = await getDb();
    await db.runAsync(
      "INSERT OR REPLACE INTO sync_state (key, value) VALUES (?, ?)",
      [STORAGE_KEY, raw]
    );
  },

  async clear(): Promise<void> {
    if (Platform.OS === "web" && typeof window !== "undefined") {
      window.localStorage.removeItem(STORAGE_KEY);
      return;
    }

    const db = await getDb();
    await db.runAsync("DELETE FROM sync_state WHERE key = ?", [STORAGE_KEY]);
  },
};

function parseSession(raw: string | null): PersistedAuthSession | null {
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as PersistedAuthSession;
    if (!value?.user?.id || !value.accessToken || !value.refreshToken) {
      return null;
    }
    return value;
  } catch {
    return null;
  }
}

