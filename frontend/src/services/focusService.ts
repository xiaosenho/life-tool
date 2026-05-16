import { getDb } from '@/db/database';
import { syncService } from './syncService';
import { createLocalId } from '@/utils/id';
import { useAuthStore } from '@/store/authStore';
import { apiClient } from './apiClient';

export interface FocusSession {
  id: string;
  user_id: string;
  mode: 'pomodoro' | 'countdown' | 'stopwatch';
  target_seconds: number;
  actual_seconds: number;
  status: 'running' | 'completed' | 'interrupted' | 'abandoned';
  started_at: string;
  ended_at: string | null;
  note: string | null;
  created_at: string;
  updated_at: string;
}

export interface FocusPreference {
  id: string;
  user_id: string;
  default_focus_minutes: number;
  short_break_minutes: number;
  long_break_minutes: number;
  auto_start_break: boolean;
  created_at: string;
  updated_at: string;
}

export interface FocusPreferenceInput {
  defaultFocusMinutes?: number;
  shortBreakMinutes?: number;
  longBreakMinutes?: number;
  autoStartBreak?: boolean;
}

export interface ServerFocusSession {
  id: string;
  userId: string;
  mode: 'pomodoro' | 'countdown' | 'stopwatch';
  targetSeconds: number;
  actualSeconds: number;
  status: 'running' | 'completed' | 'interrupted' | 'abandoned';
  startedAt: string;
  endedAt: string | null;
  note: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ServerFocusPreference {
  defaultFocusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  autoStartBreak: boolean;
  updatedAt: string;
}

function localDateKey(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function currentMonthKey() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

const DEFAULT_FOCUS_MINUTES = 25;
const DEFAULT_SHORT_BREAK_MINUTES = 5;
const DEFAULT_LONG_BREAK_MINUTES = 15;

function createDefaultPreference(userId: string): FocusPreference {
  const now = new Date().toISOString();
  return {
    id: `focus_preference_${userId}`,
    user_id: userId,
    default_focus_minutes: DEFAULT_FOCUS_MINUTES,
    short_break_minutes: DEFAULT_SHORT_BREAK_MINUTES,
    long_break_minutes: DEFAULT_LONG_BREAK_MINUTES,
    auto_start_break: false,
    created_at: now,
    updated_at: now,
  };
}

function validatePreferenceMinutes(value: number, min: number, max: number, label: string) {
  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`${label}需在 ${min}-${max} 分钟之间`);
  }
}

export const focusService = {
  async saveSession(sessionData: Omit<FocusSession, 'id' | 'user_id' | 'created_at' | 'updated_at'>) {
    const db = await getDb();
    const id = createLocalId('focus');
    const userId = useAuthStore.getState().user?.id;

    if (!userId) {
      throw new Error('User not authenticated');
    }

    const now = new Date().toISOString();
    const session: FocusSession = {
      ...sessionData,
      id,
      user_id: userId,
      created_at: now,
      updated_at: now,
    };

    // 1. Write to local business table
    await db.runAsync(
      `INSERT INTO focus_sessions (
        id, user_id, mode, target_seconds, actual_seconds, status,
        started_at, ended_at, note, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        session.id, session.user_id, session.mode, session.target_seconds,
        session.actual_seconds, session.status, session.started_at,
        session.ended_at, session.note, session.created_at, session.updated_at
      ]
    );

    // 2. Enqueue mutation
    await syncService.enqueueMutation('focus_session', session.id, 'create', session);

    return session;
  },

  async getPreference() {
    const db = await getDb();
    const userId = useAuthStore.getState().user?.id;
    if (!userId) return createDefaultPreference('guest');

    const row = await db.getFirstAsync<any>(
      'SELECT * FROM focus_preferences WHERE user_id = ?',
      [userId]
    );

    if (!row) {
      return createDefaultPreference(userId);
    }

    return {
      ...row,
      auto_start_break: Boolean(row.auto_start_break),
    } as FocusPreference;
  },

  async savePreference(input: FocusPreferenceInput) {
    const db = await getDb();
    const userId = useAuthStore.getState().user?.id;
    if (!userId) {
      throw new Error('User not authenticated');
    }

    const current = await this.getPreference();
    const now = new Date().toISOString();
    const preference: FocusPreference = {
      ...current,
      id: current.id || `focus_preference_${userId}`,
      user_id: userId,
      default_focus_minutes: input.defaultFocusMinutes ?? current.default_focus_minutes,
      short_break_minutes: input.shortBreakMinutes ?? current.short_break_minutes,
      long_break_minutes: input.longBreakMinutes ?? current.long_break_minutes,
      auto_start_break: input.autoStartBreak ?? current.auto_start_break,
      updated_at: now,
      created_at: current.created_at || now,
    };

    validatePreferenceMinutes(preference.default_focus_minutes, 1, 180, '专注时长');
    validatePreferenceMinutes(preference.short_break_minutes, 0, 60, '短休息时长');
    validatePreferenceMinutes(preference.long_break_minutes, 0, 60, '长休息时长');

    await db.runAsync(
      `INSERT OR REPLACE INTO focus_preferences (
        id, user_id, default_focus_minutes, short_break_minutes, long_break_minutes,
        auto_start_break, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        preference.id,
        preference.user_id,
        preference.default_focus_minutes,
        preference.short_break_minutes,
        preference.long_break_minutes,
        preference.auto_start_break ? 1 : 0,
        preference.created_at,
        preference.updated_at,
      ]
    );

    await syncService.enqueueMutation('focus_preference', preference.id, 'update', preference);

    return preference;
  },

  async getSessions() {
    const db = await getDb();
    const userId = useAuthStore.getState().user?.id;
    if (!userId) return [];

    const rows = await db.getAllAsync<any>(
      'SELECT * FROM focus_sessions WHERE user_id = ? ORDER BY started_at DESC',
      [userId]
    );
    return rows as FocusSession[];
  },

  async getTodayStats() {
    const db = await getDb();
    const userId = useAuthStore.getState().user?.id;
    if (!userId) return { totalSeconds: 0, sessionCount: 0 };

    const today = new Date().toISOString().split('T')[0];
    const rows = await db.getAllAsync<any>(
      "SELECT actual_seconds FROM focus_sessions WHERE user_id = ? AND started_at LIKE ? AND status = 'completed'",
      [userId, `${today}%`]
    );

    const totalSeconds = rows.reduce((acc, row) => acc + row.actual_seconds, 0);
    return {
      totalSeconds,
      sessionCount: rows.length,
    };
  },

  async getTodayStatsFromServer() {
    const res = await this.getSessionsFromServer(currentMonthKey());
    if (!res.success || !res.data) {
      throw new Error(res.error?.message || '获取专注统计失败');
    }
    const today = localDateKey(new Date());
    const completedSessions = res.data.filter((session) => (
      session.status === 'completed' && localDateKey(new Date(session.startedAt)) === today
    ));
    return {
      totalSeconds: completedSessions.reduce((sum, session) => sum + session.actualSeconds, 0),
      sessionCount: completedSessions.length,
    };
  },

  // Direct API methods (call backend)
  async startSession(mode: string, targetMinutes: number, note?: string | null) {
    return apiClient.post<ServerFocusSession>('/focus/sessions', { mode, targetMinutes, note });
  },

  async endSession(id: string, actualMinutes: number, status: string, note?: string | null) {
    return apiClient.patch<ServerFocusSession>(`/focus/sessions/${id}`, { actualMinutes, status, note });
  },

  async getSessionsFromServer(month: string) {
    return apiClient.get<ServerFocusSession[]>(`/focus/sessions?month=${month}`);
  },

  async getPreferenceFromServer() {
    return apiClient.get<ServerFocusPreference>('/focus/preferences');
  },

  async savePreferenceToServer(input: FocusPreferenceInput) {
    return apiClient.patch<ServerFocusPreference>('/focus/preferences', input);
  },
};
