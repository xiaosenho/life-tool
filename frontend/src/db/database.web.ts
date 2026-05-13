import { SCHEMA } from './schema';

type Row = Record<string, any>;

const state = new Map<string, string>();
const mutations: Row[] = [];
const tasks = new Map<string, Row>();
let mutationIdSeq = 1;

const webDb = {
  async execAsync(_sql: string) {
    return undefined;
  },

  async runAsync(sql: string, params: any[] = []) {
    if (sql.includes('INSERT INTO sync_mutations')) {
      mutations.push({
        id: mutationIdSeq++,
        mutation_id: params[0],
        entity_type: params[1],
        entity_id: params[2],
        operation: params[3],
        payload: params[4],
        created_at: new Date().toISOString(),
        synced: 0,
      });
      return undefined;
    }

    if (sql.includes('UPDATE sync_mutations SET synced = 1')) {
      const ids = new Set(params);
      mutations.forEach((mutation) => {
        if (ids.has(mutation.mutation_id)) {
          mutation.synced = 1;
        }
      });
      return undefined;
    }

    if (sql.includes('DELETE FROM sync_mutations')) {
      for (let index = mutations.length - 1; index >= 0; index -= 1) {
        if (mutations[index].synced === 1) {
          mutations.splice(index, 1);
        }
      }
      return undefined;
    }

    if (sql.includes('INSERT OR REPLACE INTO sync_state')) {
      state.set(params[0], params[1]);
      return undefined;
    }

    if (sql.includes('INSERT INTO tasks')) {
      tasks.set(params[0], {
        id: params[0],
        title: params[1],
        completed: params[2],
        updated_at: params[3],
      });
      return undefined;
    }

    if (sql.includes('UPDATE tasks SET completed')) {
      const task = tasks.get(params[2]);
      if (task) {
        task.completed = params[0];
        task.updated_at = params[1];
      }
      return undefined;
    }

    return undefined;
  },

  async getAllAsync<T>(sql: string): Promise<T[]> {
    if (sql.includes('FROM sync_mutations')) {
      return mutations
        .filter((mutation) => mutation.synced === 0)
        .sort((a, b) => a.id - b.id) as T[];
    }

    if (sql.includes('FROM tasks')) {
      return Array.from(tasks.values())
        .sort((a, b) => String(b.updated_at).localeCompare(String(a.updated_at))) as T[];
    }

    return [];
  },

  async getFirstAsync<T>(sql: string, params: any[] = []): Promise<T | null> {
    if (sql.includes('FROM sync_state')) {
      const value = state.get(params[0]);
      return value == null ? null : ({ value } as T);
    }

    return null;
  },
};

export async function initDatabase() {
  await webDb.execAsync(SCHEMA.sync_mutations);
  await webDb.execAsync(SCHEMA.sync_state);
  await webDb.execAsync(SCHEMA.tasks);
  console.log('Web preview database initialized');
  return webDb;
}

export async function getDb() {
  return webDb;
}
