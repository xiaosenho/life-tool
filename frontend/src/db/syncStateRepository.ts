import { getDb } from './database';

export const syncStateRepository = {
  async getValue(key: string): Promise<string | null> {
    const db = await getDb();
    const result = await db.getFirstAsync<{ value: string }>(
      'SELECT value FROM sync_state WHERE key = ?',
      [key]
    );
    return result ? result.value : null;
  },

  async setValue(key: string, value: string) {
    const db = await getDb();
    await db.runAsync(
      'INSERT OR REPLACE INTO sync_state (key, value) VALUES (?, ?)',
      [key, value]
    );
  }
};
