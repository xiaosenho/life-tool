import { getDb } from '@/db/database';
import { syncService } from './syncService';
import { createLocalId } from '@/utils/id';
import { useAuthStore } from '@/store/authStore';
import { apiClient } from './apiClient';

export interface Habit {
  id: string;
  user_id: string;
  name: string;
  description: string | null;
  frequency_type: 'daily' | 'weekly' | 'custom';
  frequency_days: number[] | null;
  target_count: number;
  color: string | null;
  icon: string | null;
  is_archived: boolean;
  created_at: string;
  updated_at: string;
  deleted_at: string | null;
}

export interface HabitCheckin {
  id: string;
  user_id: string;
  habit_id: string;
  checkin_date: string;
  count: number;
  note: string | null;
  created_at: string;
  updated_at: string;
}

export const habitService = {
  async createHabit(habitData: Omit<Habit, 'id' | 'user_id' | 'is_archived' | 'created_at' | 'updated_at' | 'deleted_at'>) {
    const db = await getDb();
    const id = createLocalId('habit');
    const userId = useAuthStore.getState().user?.id;
    
    if (!userId) {
      throw new Error('User not authenticated');
    }

    const now = new Date().toISOString();
    const habit: Habit = {
      ...habitData,
      id,
      user_id: userId,
      is_archived: false,
      created_at: now,
      updated_at: now,
      deleted_at: null,
    };

    // 1. Write to local business table
    await db.runAsync(
      `INSERT INTO habits (
        id, user_id, name, description, frequency_type, frequency_days, 
        target_count, color, icon, is_archived, created_at, updated_at, deleted_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        habit.id, habit.user_id, habit.name, habit.description, 
        habit.frequency_type, JSON.stringify(habit.frequency_days), 
        habit.target_count, habit.color, habit.icon, 
        habit.is_archived ? 1 : 0, habit.created_at, habit.updated_at, habit.deleted_at
      ]
    );

    // 2. Enqueue mutation
    await syncService.enqueueMutation('habit', habit.id, 'create', habit);

    return habit;
  },

  async getHabits() {
    const db = await getDb();
    const userId = useAuthStore.getState().user?.id;
    if (!userId) return [];

    const rows = await db.getAllAsync<any>(
      'SELECT * FROM habits WHERE user_id = ? AND deleted_at IS NULL ORDER BY created_at DESC',
      [userId]
    );
    return rows.map(row => ({
      ...row,
      frequency_days: row.frequency_days ? JSON.parse(row.frequency_days) : null,
      is_archived: !!row.is_archived,
    })) as Habit[];
  },

  async checkin(habitId: string, checkinDate?: string, count: number = 1, note: string | null = null) {
    const db = await getDb();
    const userId = useAuthStore.getState().user?.id;
    
    if (!userId) {
      throw new Error('User not authenticated');
    }

    const id = createLocalId('checkin');
    const date = checkinDate || new Date().toISOString().split('T')[0];
    const now = new Date().toISOString();

    const checkin: HabitCheckin = {
      id,
      user_id: userId,
      habit_id: habitId,
      checkin_date: date,
      count,
      note,
      created_at: now,
      updated_at: now,
    };

    // 1. Write to local business table
    await db.runAsync(
      `INSERT INTO habit_checkins (
        id, user_id, habit_id, checkin_date, count, note, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        checkin.id, checkin.user_id, checkin.habit_id, checkin.checkin_date, 
        checkin.count, checkin.note, checkin.created_at, checkin.updated_at
      ]
    );

    // 2. Enqueue mutation
    await syncService.enqueueMutation('habit_checkin', checkin.id, 'create', checkin);

    return checkin;
  },

  async getTodayCheckins() {
    const db = await getDb();
    const userId = useAuthStore.getState().user?.id;
    if (!userId) return [];

    const today = new Date().toISOString().split('T')[0];
    const rows = await db.getAllAsync<any>(
      'SELECT * FROM habit_checkins WHERE user_id = ? AND checkin_date = ?',
      [userId, today]
    );
    return rows as HabitCheckin[];
  },

  // Direct API methods (call backend)
  async createHabitOnServer(data: { name: string; frequencyType?: string; targetCount?: number; color?: string }) {
    return apiClient.post<Habit>('/habits', data);
  },

  async getHabitsFromServer() {
    return apiClient.get<Habit[]>('/habits');
  },

  async updateHabitOnServer(id: string, data: { name?: string; targetCount?: number; color?: string; archived?: boolean }) {
    return apiClient.patch<Habit>(`/habits/${id}`, data);
  },

  async deleteHabitOnServer(id: string) {
    return apiClient.delete<void>(`/habits/${id}`);
  },

  async checkinOnServer(habitId: string, count: number = 1, note?: string) {
    return apiClient.post<HabitCheckin>(`/habits/${habitId}/checkins`, { count, note });
  },

  async getCheckinsFromServer(habitId: string) {
    return apiClient.get<HabitCheckin[]>(`/habits/${habitId}/checkins`);
  },
};
