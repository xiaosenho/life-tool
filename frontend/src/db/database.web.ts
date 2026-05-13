import { SCHEMA } from './schema';

type Row = Record<string, any>;

const state = new Map<string, string>();
const mutations: Row[] = [];
const tasks = new Map<string, Row>();
const focusSessions = new Map<string, Row>();
const focusPreferences = new Map<string, Row>();
const habits = new Map<string, Row>();
const habitCheckins = new Map<string, Row>();
const ledgerTransactions = new Map<string, Row>();
const ledgerBudgets = new Map<string, Row>();
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

    if (sql.includes('INSERT INTO focus_sessions')) {
      focusSessions.set(params[0], {
        id: params[0],
        user_id: params[1],
        mode: params[2],
        target_seconds: params[3],
        actual_seconds: params[4],
        status: params[5],
        started_at: params[6],
        ended_at: params[7],
        note: params[8],
        created_at: params[9] || new Date().toISOString(),
        updated_at: params[10] || new Date().toISOString(),
      });
      return undefined;
    }

    if (sql.includes('INSERT OR REPLACE INTO focus_preferences')) {
      focusPreferences.set(params[1], {
        id: params[0],
        user_id: params[1],
        default_focus_minutes: params[2],
        short_break_minutes: params[3],
        long_break_minutes: params[4],
        auto_start_break: params[5],
        created_at: params[6] || new Date().toISOString(),
        updated_at: params[7] || new Date().toISOString(),
      });
      return undefined;
    }

    if (sql.includes('INSERT INTO habits')) {
      habits.set(params[0], {
        id: params[0],
        user_id: params[1],
        name: params[2],
        description: params[3],
        frequency_type: params[4],
        frequency_days: params[5],
        target_count: params[6],
        color: params[7],
        icon: params[8],
        is_archived: params[9],
        created_at: params[10] || new Date().toISOString(),
        updated_at: params[11] || new Date().toISOString(),
        deleted_at: params[12],
      });
      return undefined;
    }

    if (sql.includes('INSERT INTO habit_checkins')) {
      habitCheckins.set(params[0], {
        id: params[0],
        user_id: params[1],
        habit_id: params[2],
        checkin_date: params[3],
        count: params[4],
        note: params[5],
        created_at: params[6] || new Date().toISOString(),
        updated_at: params[7] || new Date().toISOString(),
      });
      return undefined;
    }

    if (sql.includes('INSERT INTO ledger_transactions')) {
      ledgerTransactions.set(params[0], {
        id: params[0],
        user_id: params[1],
        type: params[2],
        amount: params[3],
        currency: params[4],
        category: params[5],
        account: params[6],
        occurred_at: params[7],
        note: params[8],
        media_asset_id: params[9],
        created_at: params[10] || new Date().toISOString(),
        updated_at: params[11] || new Date().toISOString(),
        deleted_at: params[12],
      });
      return undefined;
    }

    if (sql.includes('INSERT INTO ledger_budgets')) {
      ledgerBudgets.set(params[0], {
        id: params[0],
        user_id: params[1],
        month: params[2],
        amount: params[3],
        currency: params[4],
        category: params[5],
        created_at: params[6] || new Date().toISOString(),
        updated_at: params[7] || new Date().toISOString(),
      });
      return undefined;
    }

    if (sql.includes('UPDATE ledger_transactions SET')) {
      const txn = ledgerTransactions.get(params[params.length - 1]);
      if (txn) {
        if (sql.includes('deleted_at')) {
          txn.deleted_at = params[0];
          txn.updated_at = params[1];
        } else {
          txn.type = params[0];
          txn.amount = params[1];
          txn.currency = params[2];
          txn.category = params[3];
          txn.account = params[4];
          txn.occurred_at = params[5];
          txn.note = params[6];
          txn.media_asset_id = params[7];
          txn.updated_at = params[8];
        }
      }
      return undefined;
    }

    if (sql.includes('INSERT OR REPLACE INTO ledger_budgets')) {
      ledgerBudgets.set(params[0], {
        id: params[0],
        user_id: params[1],
        month: params[2],
        amount: params[3],
        currency: params[4],
        category: params[5],
        created_at: params[6] || new Date().toISOString(),
        updated_at: params[7] || new Date().toISOString(),
      });
      return undefined;
    }

    return undefined;
  },

  async getAllAsync<T>(sql: string, params: any[] = []): Promise<T[]> {
    if (sql.includes('FROM sync_mutations')) {
      return mutations
        .filter((mutation) => mutation.synced === 0)
        .sort((a, b) => a.id - b.id) as T[];
    }

    if (sql.includes('FROM tasks')) {
      return Array.from(tasks.values())
        .sort((a, b) => String(b.updated_at).localeCompare(String(a.updated_at))) as T[];
    }

    if (sql.includes('FROM focus_sessions')) {
      return Array.from(focusSessions.values())
        .filter((session) => (params[0] ? session.user_id === params[0] : true))
        .filter((session) => (params[1] ? String(session.started_at).startsWith(String(params[1]).replace('%', '')) : true))
        .filter((session) => (sql.includes("status = 'completed'") ? session.status === 'completed' : true))
        .sort((a, b) => String(b.started_at).localeCompare(String(a.started_at))) as T[];
    }

    if (sql.includes('FROM habits')) {
      return Array.from(habits.values())
        .filter((habit) => (params[0] ? habit.user_id === params[0] : true))
        .filter((habit) => habit.deleted_at == null)
        .sort((a, b) => String(b.created_at).localeCompare(String(a.created_at))) as T[];
    }

    if (sql.includes('FROM habit_checkins')) {
      if (sql.includes('WHERE checkin_date =')) {
        return Array.from(habitCheckins.values())
          .filter((checkin) => checkin.checkin_date === params[0]) as T[];
      }
      if (sql.includes('WHERE user_id = ? AND checkin_date = ?')) {
        return Array.from(habitCheckins.values())
          .filter((checkin) => checkin.user_id === params[0])
          .filter((checkin) => checkin.checkin_date === params[1]) as T[];
      }
      return Array.from(habitCheckins.values()) as T[];
    }

    if (sql.includes('FROM ledger_transactions')) {
      let rows = Array.from(ledgerTransactions.values());
      if (sql.includes('WHERE user_id = ? AND occurred_at LIKE ?')) {
        rows = rows.filter((t) => t.user_id === params[0] && String(t.occurred_at).startsWith(String(params[1]).replace('%', '')));
      }
      rows = rows.filter((t) => t.deleted_at == null);
      return rows.sort((a, b) => String(b.occurred_at).localeCompare(String(a.occurred_at))) as T[];
    }

    if (sql.includes('FROM ledger_budgets')) {
      let rows = Array.from(ledgerBudgets.values());
      if (sql.includes('WHERE user_id = ? AND month = ?')) {
        rows = rows.filter((b) => b.user_id === params[0] && b.month === params[1]);
      }
      return rows as T[];
    }

    return [];
  },

  async getFirstAsync<T>(sql: string, params: any[] = []): Promise<T | null> {
    if (sql.includes('FROM sync_state')) {
      const value = state.get(params[0]);
      return value == null ? null : ({ value } as T);
    }

    if (sql.includes('FROM focus_preferences')) {
      const preference = focusPreferences.get(params[0]);
      return preference == null ? null : (preference as T);
    }

    if (sql.includes('FROM ledger_transactions')) {
      const transaction = ledgerTransactions.get(params[0]);
      if (!transaction || transaction.user_id !== params[1] || transaction.deleted_at != null) {
        return null;
      }
      return transaction as T;
    }

    return null;
  },
};

export async function initDatabase() {
  await webDb.execAsync(SCHEMA.sync_mutations);
  await webDb.execAsync(SCHEMA.sync_state);
  await webDb.execAsync(SCHEMA.tasks);
  await webDb.execAsync(SCHEMA.focus_sessions);
  await webDb.execAsync(SCHEMA.focus_preferences);
  await webDb.execAsync(SCHEMA.habits);
  await webDb.execAsync(SCHEMA.habit_checkins);
  await webDb.execAsync(SCHEMA.ledger_transactions);
  await webDb.execAsync(SCHEMA.ledger_budgets);
  console.log('Web preview database initialized');
  return webDb;
}

export async function getDb() {
  return webDb;
}
