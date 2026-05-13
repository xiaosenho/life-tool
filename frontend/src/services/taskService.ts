import { getDb } from '@/db/database';
import { syncService } from './syncService';
import { createLocalId } from '@/utils/id';

export interface Task {
  id: string;
  title: string;
  completed: boolean;
  updated_at: string;
}

export const taskService = {
  async createTask(title: string) {
    const db = await getDb();
    const id = createLocalId('task');
    const task: Task = {
      id,
      title,
      completed: false,
      updated_at: new Date().toISOString(),
    };

    // 1. Write to local business table
    await db.runAsync(
      'INSERT INTO tasks (id, title, completed, updated_at) VALUES (?, ?, ?, ?)',
      [task.id, task.title, task.completed ? 1 : 0, task.updated_at]
    );

    // 2. Enqueue mutation
    await syncService.enqueueMutation('task', task.id, 'create', task);

    return task;
  },

  async getTasks() {
    const db = await getDb();
    const rows = await db.getAllAsync<any>('SELECT * FROM tasks ORDER BY updated_at DESC');
    return rows.map(row => ({
      ...row,
      completed: !!row.completed,
    })) as Task[];
  },

  async toggleTask(id: string, completed: boolean) {
    const db = await getDb();
    const updated_at = new Date().toISOString();

    // 1. Update local business table
    await db.runAsync(
      'UPDATE tasks SET completed = ?, updated_at = ? WHERE id = ?',
      [completed ? 1 : 0, updated_at, id]
    );

    // 2. Enqueue mutation
    await syncService.enqueueMutation('task', id, 'update', { completed, updated_at });
  }
};
