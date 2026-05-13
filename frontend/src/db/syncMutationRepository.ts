import { getDb } from './database';

export interface SyncMutation {
  id?: number;
  mutation_id: string;
  entity_type: string;
  entity_id: string;
  operation: 'create' | 'update' | 'delete';
  payload: string;
  created_at?: string;
  synced: number;
}

export const syncMutationRepository = {
  async enqueue(mutation: Omit<SyncMutation, 'id' | 'created_at' | 'synced'>) {
    const db = await getDb();
    await db.runAsync(
      `INSERT INTO sync_mutations (mutation_id, entity_type, entity_id, operation, payload) 
       VALUES (?, ?, ?, ?, ?)`,
      [
        mutation.mutation_id,
        mutation.entity_type,
        mutation.entity_id,
        mutation.operation,
        mutation.payload,
      ]
    );
  },

  async getPending() {
    const db = await getDb();
    return await db.getAllAsync<SyncMutation>(
      'SELECT * FROM sync_mutations WHERE synced = 0 ORDER BY id ASC'
    );
  },

  async markAsSynced(mutationIds: string[]) {
    if (mutationIds.length === 0) return;
    const db = await getDb();
    const placeholders = mutationIds.map(() => '?').join(',');
    await db.runAsync(
      `UPDATE sync_mutations SET synced = 1 WHERE mutation_id IN (${placeholders})`,
      mutationIds
    );
  },

  async clearSynced() {
    const db = await getDb();
    await db.runAsync('DELETE FROM sync_mutations WHERE synced = 1');
  }
};
