import { getDb } from '@/db/database';
import { syncService } from './syncService';
import { createLocalId } from '@/utils/id';
import { useAuthStore } from '@/store/authStore';

export type EventType = 'anniversary' | 'birthday' | 'important_day' | 'todo_reminder';
export type RepeatRule = 'none' | 'yearly' | 'monthly' | 'weekly';

export interface AnniversaryEvent {
  id: string;
  user_id: string;
  type: EventType;
  title: string;
  event_date: string;
  repeat_rule: RepeatRule;
  remind_days_before: number[];
  note: string | null;
  media_asset_id: string | null;
  created_at: string;
  updated_at: string;
  deleted_at: string | null;
  daysUntil: number;
  nextOccurrenceDate: string;
}

export interface EventInput {
  type: EventType;
  title: string;
  eventDate: string;
  repeatRule?: RepeatRule;
  remindDaysBefore?: number[];
  note?: string | null;
  mediaAssetId?: string | null;
}

const EVENT_TYPES: EventType[] = ['anniversary', 'birthday', 'important_day', 'todo_reminder'];
const REPEAT_RULES: RepeatRule[] = ['none', 'yearly', 'monthly', 'weekly'];
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function getUserId() {
  const userId = useAuthStore.getState().user?.id;
  if (!userId) throw new Error('用户未登录');
  return userId;
}

function validateInput(input: EventInput) {
  if (!EVENT_TYPES.includes(input.type)) throw new Error('事件类型无效');
  if (!input.title.trim()) throw new Error('请输入事件标题');
  if (!DATE_PATTERN.test(input.eventDate)) throw new Error('日期格式需为 YYYY-MM-DD');
  if (input.repeatRule && !REPEAT_RULES.includes(input.repeatRule)) throw new Error('重复规则无效');
  if (input.remindDaysBefore?.some((day) => !Number.isInteger(day) || day < 0 || day > 366)) {
    throw new Error('提前提醒天数需在 0-366 之间');
  }
}

function toDate(value: string) {
  return new Date(`${value}T00:00:00.000Z`);
}

function toDateString(date: Date) {
  return date.toISOString().slice(0, 10);
}

function todayDate() {
  return toDate(new Date().toISOString().slice(0, 10));
}

function daysBetween(from: Date, to: Date) {
  const msPerDay = 24 * 60 * 60 * 1000;
  return Math.round((to.getTime() - from.getTime()) / msPerDay);
}

function safeDate(year: number, monthIndex: number, day: number) {
  const lastDay = new Date(Date.UTC(year, monthIndex + 1, 0)).getUTCDate();
  return new Date(Date.UTC(year, monthIndex, Math.min(day, lastDay)));
}

function nextOccurrence(eventDate: string, repeatRule: RepeatRule, reference = todayDate()) {
  const original = toDate(eventDate);
  if (repeatRule === 'none') return original;

  if (repeatRule === 'weekly') {
    const candidate = new Date(original);
    while (candidate < reference) {
      const diffWeeks = Math.max(1, Math.floor(daysBetween(candidate, reference) / 7));
      candidate.setUTCDate(candidate.getUTCDate() + diffWeeks * 7);
    }
    return candidate;
  }

  if (repeatRule === 'monthly') {
    let candidate = safeDate(reference.getUTCFullYear(), reference.getUTCMonth(), original.getUTCDate());
    if (candidate < reference) {
      const nextMonth = new Date(Date.UTC(reference.getUTCFullYear(), reference.getUTCMonth() + 1, 1));
      candidate = safeDate(nextMonth.getUTCFullYear(), nextMonth.getUTCMonth(), original.getUTCDate());
    }
    return candidate;
  }

  let candidate = safeDate(reference.getUTCFullYear(), original.getUTCMonth(), original.getUTCDate());
  if (candidate < reference) {
    candidate = safeDate(reference.getUTCFullYear() + 1, original.getUTCMonth(), original.getUTCDate());
  }
  return candidate;
}

function hydrate(row: any): AnniversaryEvent {
  const remindDays = row.remind_days_before ? JSON.parse(row.remind_days_before) : [];
  const repeatRule = row.repeat_rule as RepeatRule;
  const occurrence = nextOccurrence(row.event_date, repeatRule);
  return {
    ...row,
    remind_days_before: remindDays,
    daysUntil: daysBetween(todayDate(), occurrence),
    nextOccurrenceDate: toDateString(occurrence),
  } as AnniversaryEvent;
}

export const eventService = {
  async createEvent(input: EventInput) {
    const db = await getDb();
    const userId = getUserId();
    validateInput(input);

    const now = new Date().toISOString();
    const event: Omit<AnniversaryEvent, 'daysUntil' | 'nextOccurrenceDate'> = {
      id: createLocalId('event'),
      user_id: userId,
      type: input.type,
      title: input.title.trim(),
      event_date: input.eventDate,
      repeat_rule: input.repeatRule || 'none',
      remind_days_before: [...(input.remindDaysBefore || [])].sort((a, b) => b - a),
      note: input.note?.trim() || null,
      media_asset_id: input.mediaAssetId?.trim() || null,
      created_at: now,
      updated_at: now,
      deleted_at: null,
    };

    await db.runAsync(
      `INSERT INTO anniversary_events (
        id, user_id, type, title, event_date, repeat_rule, remind_days_before,
        note, media_asset_id, created_at, updated_at, deleted_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        event.id, event.user_id, event.type, event.title, event.event_date,
        event.repeat_rule, JSON.stringify(event.remind_days_before), event.note,
        event.media_asset_id, event.created_at, event.updated_at, event.deleted_at,
      ]
    );

    await syncService.enqueueMutation('anniversary_event', event.id, 'create', event);
    return hydrate({ ...event, remind_days_before: JSON.stringify(event.remind_days_before) });
  },

  async updateEvent(id: string, input: Partial<EventInput>) {
    const db = await getDb();
    const userId = getUserId();
    const existing = await db.getFirstAsync<any>(
      'SELECT * FROM anniversary_events WHERE id = ? AND user_id = ? AND deleted_at IS NULL',
      [id, userId]
    );
    if (!existing) throw new Error('事件不存在');

    const merged: EventInput = {
      type: input.type ?? existing.type,
      title: input.title ?? existing.title,
      eventDate: input.eventDate ?? existing.event_date,
      repeatRule: input.repeatRule ?? existing.repeat_rule,
      remindDaysBefore: input.remindDaysBefore ?? JSON.parse(existing.remind_days_before || '[]'),
      note: input.note ?? existing.note,
      mediaAssetId: input.mediaAssetId ?? existing.media_asset_id,
    };
    validateInput(merged);

    const now = new Date().toISOString();
    await db.runAsync(
      `UPDATE anniversary_events SET
        type = ?, title = ?, event_date = ?, repeat_rule = ?, remind_days_before = ?,
        note = ?, media_asset_id = ?, updated_at = ?
       WHERE id = ?`,
      [
        merged.type, merged.title.trim(), merged.eventDate, merged.repeatRule || 'none',
        JSON.stringify(merged.remindDaysBefore || []), merged.note ?? null,
        merged.mediaAssetId ?? null, now, id,
      ]
    );

    await syncService.enqueueMutation('anniversary_event', id, 'update', { id, ...merged });
    return this.getEvent(id);
  },

  async deleteEvent(id: string) {
    const db = await getDb();
    const userId = getUserId();
    const existing = await db.getFirstAsync<any>(
      'SELECT * FROM anniversary_events WHERE id = ? AND user_id = ? AND deleted_at IS NULL',
      [id, userId]
    );
    if (!existing) throw new Error('事件不存在');

    const now = new Date().toISOString();
    await db.runAsync(
      'UPDATE anniversary_events SET deleted_at = ?, updated_at = ? WHERE id = ?',
      [now, now, id]
    );
    await syncService.enqueueMutation('anniversary_event', id, 'delete', { id });
  },

  async getEvent(id: string) {
    const db = await getDb();
    const userId = getUserId();
    const row = await db.getFirstAsync<any>(
      'SELECT * FROM anniversary_events WHERE id = ? AND user_id = ? AND deleted_at IS NULL',
      [id, userId]
    );
    return row ? hydrate(row) : null;
  },

  async getEvents(from: string, to: string) {
    const db = await getDb();
    const userId = getUserId();
    const rows = await db.getAllAsync<any>(
      'SELECT * FROM anniversary_events WHERE user_id = ? AND deleted_at IS NULL',
      [userId]
    );
    return rows
      .map(hydrate)
      .filter((event) => event.nextOccurrenceDate >= from && event.nextOccurrenceDate <= to)
      .sort((a, b) => a.nextOccurrenceDate.localeCompare(b.nextOccurrenceDate));
  },

  async getUpcoming(days: number = 30) {
    const from = new Date().toISOString().slice(0, 10);
    const end = new Date();
    end.setUTCDate(end.getUTCDate() + days);
    return this.getEvents(from, toDateString(end));
  },
};
