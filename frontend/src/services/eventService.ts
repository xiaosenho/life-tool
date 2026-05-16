import { getDb } from '@/db/database';
import { useAuthStore } from '@/store/authStore';
import { apiClient } from './apiClient';

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
  displayDate?: string;
  reminderOffsetDays?: number;
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

interface ServerEvent {
  id: string;
  type: EventType;
  title: string;
  eventDate: string;
  repeatRule: RepeatRule;
  remindDaysBefore: number[];
  daysUntil: number;
  nextOccurrenceDate: string;
  displayDate?: string;
  reminderOffsetDays?: number;
  note: string | null;
  mediaAssetId: string | null;
  createdAt: string;
  updatedAt: string;
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

function fromServerEvent(server: ServerEvent, userId: string): AnniversaryEvent {
  return {
    id: server.id,
    user_id: userId,
    type: server.type,
    title: server.title,
    event_date: server.eventDate,
    repeat_rule: server.repeatRule,
    remind_days_before: server.remindDaysBefore ?? [],
    note: server.note,
    media_asset_id: server.mediaAssetId,
    created_at: server.createdAt,
    updated_at: server.updatedAt,
    deleted_at: null,
    daysUntil: server.daysUntil,
    nextOccurrenceDate: server.nextOccurrenceDate,
    displayDate: server.displayDate ?? server.nextOccurrenceDate,
    reminderOffsetDays: server.reminderOffsetDays ?? 0,
  };
}

function expandReminderOccurrences(event: AnniversaryEvent): AnniversaryEvent[] {
  const offsets = Array.from(new Set([0, ...(event.remind_days_before ?? [])]))
    .filter((day) => Number.isInteger(day) && day >= 0)
    .sort((a, b) => a - b);

  return offsets.map((offset) => {
    const displayDateObj = toDate(event.nextOccurrenceDate);
    displayDateObj.setUTCDate(displayDateObj.getUTCDate() - offset);
    return {
      ...event,
      displayDate: toDateString(displayDateObj),
      reminderOffsetDays: offset,
      daysUntil: daysBetween(todayDate(), displayDateObj),
    };
  });
}

async function upsertLocalEvent(event: AnniversaryEvent) {
  const db = await getDb();
  await db.runAsync(
    `INSERT INTO anniversary_events (
      id, user_id, type, title, event_date, repeat_rule, remind_days_before,
      note, media_asset_id, created_at, updated_at, deleted_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
      user_id = excluded.user_id,
      type = excluded.type,
      title = excluded.title,
      event_date = excluded.event_date,
      repeat_rule = excluded.repeat_rule,
      remind_days_before = excluded.remind_days_before,
      note = excluded.note,
      media_asset_id = excluded.media_asset_id,
      created_at = excluded.created_at,
      updated_at = excluded.updated_at,
      deleted_at = excluded.deleted_at`,
    [
      event.id,
      event.user_id,
      event.type,
      event.title,
      event.event_date,
      event.repeat_rule,
      JSON.stringify(event.remind_days_before ?? []),
      event.note,
      event.media_asset_id,
      event.created_at,
      event.updated_at,
      event.deleted_at,
    ]
  );
}

async function markLocalEventDeleted(id: string) {
  const db = await getDb();
  const now = new Date().toISOString();
  await db.runAsync(
    'UPDATE anniversary_events SET deleted_at = ?, updated_at = ? WHERE id = ?',
    [now, now, id]
  );
}

export const eventService = {
  async createEvent(input: EventInput) {
    const userId = getUserId();
    validateInput(input);

    const response = await apiClient.post<ServerEvent>('/events', {
      type: input.type,
      title: input.title.trim(),
      eventDate: input.eventDate,
      repeatRule: input.repeatRule || 'none',
      remindDaysBefore: [...(input.remindDaysBefore || [])].sort((a, b) => b - a),
      note: input.note?.trim() || null,
      mediaAssetId: input.mediaAssetId?.trim() || null,
    });
    if (!response.success || !response.data) {
      throw new Error(response.error?.message || '纪念日保存失败');
    }
    const event = fromServerEvent(response.data, userId);
    await upsertLocalEvent(event);
    return event;
  },

  async updateEvent(id: string, input: Partial<EventInput>) {
    const userId = getUserId();
    const payload: Record<string, unknown> = {};
    if (input.type !== undefined) payload.type = input.type;
    if (input.title !== undefined) payload.title = input.title.trim();
    if (input.eventDate !== undefined) payload.eventDate = input.eventDate;
    if (input.repeatRule !== undefined) payload.repeatRule = input.repeatRule;
    if (input.remindDaysBefore !== undefined) payload.remindDaysBefore = [...input.remindDaysBefore].sort((a, b) => b - a);
    if (input.note !== undefined) payload.note = input.note?.trim() || null;
    if (input.mediaAssetId !== undefined) payload.mediaAssetId = input.mediaAssetId?.trim() || null;

    const response = await apiClient.patch<ServerEvent>(`/events/${id}`, payload);
    if (!response.success || !response.data) {
      throw new Error(response.error?.message || '纪念日更新失败');
    }
    const event = fromServerEvent(response.data, userId);
    await upsertLocalEvent(event);
    return event;
  },

  async deleteEvent(id: string) {
    const response = await apiClient.delete<void>(`/events/${id}`);
    if (!response.success) {
      throw new Error(response.error?.message || '纪念日删除失败');
    }
    await markLocalEventDeleted(id);
  },

  async getEvent(id: string) {
    const userId = getUserId();
    const response = await apiClient.get<ServerEvent[]>(`/events?from=1970-01-01&to=2100-12-31`);
    if (response.success && response.data) {
      const event = response.data.find((item) => item.id === id);
      if (!event) {
        return null;
      }
      const mapped = fromServerEvent(event, userId);
      await upsertLocalEvent(mapped);
      return mapped;
    }
    const db = await getDb();
    const row = await db.getFirstAsync<any>(
      'SELECT * FROM anniversary_events WHERE id = ? AND user_id = ? AND deleted_at IS NULL',
      [id, userId]
    );
    return row ? hydrate(row) : null;
  },

  async getEvents(from: string, to: string) {
    const userId = getUserId();
    const response = await apiClient.get<ServerEvent[]>(`/events?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
    if (response.success && response.data) {
      const events = response.data.map((item) => fromServerEvent(item, userId));
      await Promise.all(events.map((event) => upsertLocalEvent(event)));
      return events
        .filter((event) => !!event.displayDate && event.displayDate >= from && event.displayDate <= to)
        .sort((a, b) => (a.displayDate ?? "").localeCompare(b.displayDate ?? ""));
    }
    const db = await getDb();
    const rows = await db.getAllAsync<any>(
      'SELECT * FROM anniversary_events WHERE user_id = ? AND deleted_at IS NULL',
      [userId]
    );
    return rows
      .map(hydrate)
      .flatMap(expandReminderOccurrences)
      .filter((event) => !!event.displayDate && event.displayDate >= from && event.displayDate <= to)
      .sort((a, b) => (a.displayDate ?? "").localeCompare(b.displayDate ?? ""));
  },

  async getUpcoming(days: number = 30) {
    const response = await apiClient.get<ServerEvent[]>(`/events/upcoming?days=${days}`);
    if (response.success && response.data) {
      const userId = getUserId();
      const events = response.data.map((item) => fromServerEvent(item, userId));
      await Promise.all(events.map((event) => upsertLocalEvent(event)));
      return events
        .filter((event) => event.daysUntil >= 0 && event.daysUntil <= days)
        .sort((a, b) => a.daysUntil - b.daysUntil);
    }
    const from = new Date().toISOString().slice(0, 10);
    const end = new Date();
    end.setUTCDate(end.getUTCDate() + days);
    return this.getEvents(from, toDateString(end));
  },

  async getAllSaved() {
    const userId = getUserId();
    const response = await apiClient.get<ServerEvent[]>(`/events?from=1970-01-01&to=2100-12-31`);
    if (response.success && response.data) {
      const deduped = new Map<string, AnniversaryEvent>();
      response.data.forEach((item) => {
        const event = fromServerEvent(item, userId);
        deduped.set(event.id, event);
      });
      const events = Array.from(deduped.values());
      await Promise.all(events.map((event) => upsertLocalEvent(event)));
      return events.sort((a, b) => {
        const dateCompare = a.event_date.localeCompare(b.event_date);
        if (dateCompare !== 0) return dateCompare;
        return a.created_at.localeCompare(b.created_at);
      });
    }

    const db = await getDb();
    const rows = await db.getAllAsync<any>(
      'SELECT * FROM anniversary_events WHERE user_id = ? AND deleted_at IS NULL ORDER BY event_date ASC, created_at ASC',
      [userId]
    );
    return rows.map(hydrate);
  },
};
