import { getDb } from '@/db/database';
import { syncService } from './syncService';
import { createLocalId } from '@/utils/id';
import { useAuthStore } from '@/store/authStore';

export interface FocusSession {
  id: string;
  user_id: string;
  mode: 'pomodoro' | 'countdown' | 'stopwatch';
  target_seconds: number;
  actual_seconds: number;
  status: 'completed' | 'interrupted' | 'abandoned';
  started_at: string;
  ended_at: string | null;
  note: string | null;
  created_at: string;
  updated_at: string;
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
  }
};
