export const SCHEMA = {
  sync_mutations: `
    CREATE TABLE IF NOT EXISTS sync_mutations (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      mutation_id TEXT NOT NULL UNIQUE,
      entity_type TEXT NOT NULL,
      entity_id TEXT NOT NULL,
      operation TEXT NOT NULL,
      payload TEXT NOT NULL,
      created_at TEXT DEFAULT CURRENT_TIMESTAMP,
      synced INTEGER DEFAULT 0
    );
  `,
  sync_state: `
    CREATE TABLE IF NOT EXISTS sync_state (
      key TEXT PRIMARY KEY,
      value TEXT
    );
  `,
  // Example business table for smoke test
  tasks: `
    CREATE TABLE IF NOT EXISTS tasks (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      completed INTEGER DEFAULT 0,
      updated_at TEXT DEFAULT CURRENT_TIMESTAMP
    );
  `
};
