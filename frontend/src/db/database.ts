import * as SQLite from 'expo-sqlite';
import { SCHEMA } from './schema';

const DB_NAME = 'lifetool.db';

export let db: SQLite.SQLiteDatabase;

export async function initDatabase() {
  if (db) return db;

  db = await SQLite.openDatabaseAsync(DB_NAME);

  // Simple migration/initialization logic
  await db.execAsync(SCHEMA.sync_mutations);
  await db.execAsync(SCHEMA.sync_state);
  await db.execAsync(SCHEMA.tasks);
  await db.execAsync(SCHEMA.focus_sessions);
  await db.execAsync(SCHEMA.focus_preferences);
  await db.execAsync(SCHEMA.habits);
  await db.execAsync(SCHEMA.habit_checkins);

  console.log('Database initialized');
  return db;
}

export async function getDb() {
  if (!db) {
    await initDatabase();
  }
  return db;
}
