import { getDb } from '@/db/database';
import { syncService } from './syncService';
import { createLocalId } from '@/utils/id';
import { useAuthStore } from '@/store/authStore';

export type LedgerTransactionType = 'income' | 'expense' | 'transfer';

export interface LedgerTransaction {
  id: string;
  user_id: string;
  type: LedgerTransactionType;
  amount: number;
  currency: string;
  category: string | null;
  account: string | null;
  occurred_at: string;
  note: string | null;
  media_asset_id: string | null;
  created_at: string;
  updated_at: string;
  deleted_at: string | null;
}

export interface LedgerTransactionInput {
  type: LedgerTransactionType;
  amount: number;
  currency?: string;
  category?: string | null;
  account?: string | null;
  occurredAt: string;
  note?: string | null;
  mediaAssetId?: string | null;
}

export interface LedgerBudget {
  id: string;
  user_id: string;
  month: string;
  amount: number;
  currency: string;
  category: string | null;
  created_at: string;
  updated_at: string;
}

export interface LedgerBudgetInput {
  amount: number;
  currency?: string;
  category?: string | null;
}

export interface LedgerMonthSummary {
  month: string;
  income: number;
  expense: number;
  balance: number;
  budget: number;
  categoryExpenses: { category: string; amount: number }[];
}

const MONTH_REGEX = /^\d{4}-\d{2}$/;

function validateAmount(amount: number) {
  if (typeof amount !== 'number' || amount <= 0) {
    throw new Error('金额必须大于 0');
  }
}

function validateMonth(month: string) {
  if (!MONTH_REGEX.test(month)) {
    throw new Error('月份格式必须为 YYYY-MM');
  }
}

function getUserId(): string {
  const userId = useAuthStore.getState().user?.id;
  if (!userId) {
    throw new Error('用户未登录');
  }
  return userId;
}

export const ledgerService = {
  async createTransaction(input: LedgerTransactionInput) {
    const db = await getDb();
    const userId = getUserId();
    const id = createLocalId('ledger_txn');

    validateAmount(input.amount);
    if (!['income', 'expense', 'transfer'].includes(input.type)) {
      throw new Error('流水类型无效，仅支持 income、expense、transfer');
    }

    const now = new Date().toISOString();
    const transaction: LedgerTransaction = {
      id,
      user_id: userId,
      type: input.type,
      amount: input.amount,
      currency: input.currency || 'CNY',
      category: input.category ?? null,
      account: input.account ?? null,
      occurred_at: input.occurredAt,
      note: input.note ?? null,
      media_asset_id: input.mediaAssetId ?? null,
      created_at: now,
      updated_at: now,
      deleted_at: null,
    };

    await db.runAsync(
      `INSERT INTO ledger_transactions (
        id, user_id, type, amount, currency, category, account,
        occurred_at, note, media_asset_id, created_at, updated_at, deleted_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        transaction.id, transaction.user_id, transaction.type, transaction.amount,
        transaction.currency, transaction.category, transaction.account,
        transaction.occurred_at, transaction.note, transaction.media_asset_id,
        transaction.created_at, transaction.updated_at, transaction.deleted_at,
      ]
    );

    await syncService.enqueueMutation('ledger_transaction', transaction.id, 'create', transaction);
    return transaction;
  },

  async updateTransaction(id: string, input: Partial<LedgerTransactionInput>) {
    const db = await getDb();
    const userId = getUserId();

    if (input.amount !== undefined) {
      validateAmount(input.amount);
    }
    if (input.type !== undefined && !['income', 'expense', 'transfer'].includes(input.type)) {
      throw new Error('流水类型无效，仅支持 income、expense、transfer');
    }

    const existing = await db.getFirstAsync<any>(
      'SELECT * FROM ledger_transactions WHERE id = ? AND user_id = ? AND deleted_at IS NULL',
      [id, userId]
    );
    if (!existing) {
      throw new Error('流水不存在');
    }

    const now = new Date().toISOString();
    await db.runAsync(
      `UPDATE ledger_transactions SET
        type = ?, amount = ?, currency = ?, category = ?, account = ?,
        occurred_at = ?, note = ?, media_asset_id = ?, updated_at = ?
       WHERE id = ?`,
      [
        input.type ?? existing.type,
        input.amount ?? existing.amount,
        input.currency ?? existing.currency,
        input.category !== undefined ? input.category : existing.category,
        input.account !== undefined ? input.account : existing.account,
        input.occurredAt ?? existing.occurred_at,
        input.note !== undefined ? input.note : existing.note,
        input.mediaAssetId !== undefined ? input.mediaAssetId : existing.media_asset_id,
        now,
        id,
      ]
    );

    await syncService.enqueueMutation('ledger_transaction', id, 'update', { id, ...input });
    return this.getTransaction(id);
  },

  async deleteTransaction(id: string) {
    const db = await getDb();
    const userId = getUserId();

    const existing = await db.getFirstAsync<any>(
      'SELECT * FROM ledger_transactions WHERE id = ? AND user_id = ? AND deleted_at IS NULL',
      [id, userId]
    );
    if (!existing) {
      throw new Error('流水不存在');
    }

    const now = new Date().toISOString();
    await db.runAsync(
      'UPDATE ledger_transactions SET deleted_at = ?, updated_at = ? WHERE id = ?',
      [now, now, id]
    );

    await syncService.enqueueMutation('ledger_transaction', id, 'delete', { id });
  },

  async getTransaction(id: string) {
    const db = await getDb();
    const userId = getUserId();

    const row = await db.getFirstAsync<any>(
      'SELECT * FROM ledger_transactions WHERE id = ? AND user_id = ? AND deleted_at IS NULL',
      [id, userId]
    );
    return row as LedgerTransaction | null;
  },

  async getTransactions(month: string) {
    const db = await getDb();
    const userId = getUserId();

    validateMonth(month);

    const rows = await db.getAllAsync<any>(
      "SELECT * FROM ledger_transactions WHERE user_id = ? AND occurred_at LIKE ? AND deleted_at IS NULL ORDER BY occurred_at DESC",
      [userId, `${month}%`]
    );
    return rows as LedgerTransaction[];
  },

  async getTransactionsForDate(date: string) {
    const month = date.slice(0, 7);
    const transactions = await this.getTransactions(month);
    return transactions.filter((transaction) => String(transaction.occurred_at).slice(0, 10) === date);
  },

  async getSummary(month: string) {
    const db = await getDb();
    const userId = getUserId();

    validateMonth(month);

    const transactions = await db.getAllAsync<any>(
      "SELECT * FROM ledger_transactions WHERE user_id = ? AND occurred_at LIKE ? AND deleted_at IS NULL",
      [userId, `${month}%`]
    );

    const income = transactions
      .filter((t: any) => t.type === 'income')
      .reduce((sum: number, t: any) => sum + t.amount, 0);
    const expense = transactions
      .filter((t: any) => t.type === 'expense')
      .reduce((sum: number, t: any) => sum + t.amount, 0);

    const categoryMap = new Map<string, number>();
    transactions
      .filter((t: any) => t.type === 'expense' && t.category)
      .forEach((t: any) => {
        categoryMap.set(t.category, (categoryMap.get(t.category) || 0) + t.amount);
      });
    const categoryExpenses = Array.from(categoryMap.entries())
      .map(([category, amount]) => ({ category, amount }))
      .sort((a, b) => b.amount - a.amount);

    const budgets = await this.getBudgets(month);
    const totalBudget = budgets
      .filter((b) => b.category === null)
      .reduce((sum, b) => sum + b.amount, 0);

    const summary: LedgerMonthSummary = {
      month,
      income,
      expense,
      balance: income - expense,
      budget: totalBudget,
      categoryExpenses,
    };

    return summary;
  },

  async getBudgets(month: string) {
    const db = await getDb();
    const userId = getUserId();

    validateMonth(month);

    const rows = await db.getAllAsync<any>(
      'SELECT * FROM ledger_budgets WHERE user_id = ? AND month = ?',
      [userId, month]
    );
    return rows as LedgerBudget[];
  },

  async saveBudget(month: string, input: LedgerBudgetInput) {
    const db = await getDb();
    const userId = getUserId();

    validateMonth(month);
    validateAmount(input.amount);

    const now = new Date().toISOString();
    const categoryKey = input.category ?? '__overall__';

    const existingRows = await db.getAllAsync<any>(
      'SELECT * FROM ledger_budgets WHERE user_id = ? AND month = ?',
      [userId, month]
    );
    const existing = existingRows.find(
      (r: any) => (r.category ?? '__overall__') === categoryKey
    );

    if (existing) {
      existing.amount = input.amount;
      existing.currency = input.currency || 'CNY';
      existing.category = input.category ?? null;
      existing.updated_at = now;

      await db.runAsync(
        `INSERT OR REPLACE INTO ledger_budgets (
          id, user_id, month, amount, currency, category, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          existing.id, userId, month, input.amount, input.currency || 'CNY',
          input.category ?? null, existing.created_at, now,
        ]
      );
      await syncService.enqueueMutation('ledger_budget', existing.id, 'update', existing);
      return existing as LedgerBudget;
    }

    const id = createLocalId('ledger_budget');
    const budget: LedgerBudget = {
      id,
      user_id: userId,
      month,
      amount: input.amount,
      currency: input.currency || 'CNY',
      category: input.category ?? null,
      created_at: now,
      updated_at: now,
    };

    await db.runAsync(
      `INSERT INTO ledger_budgets (
        id, user_id, month, amount, currency, category, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        budget.id, budget.user_id, budget.month, budget.amount,
        budget.currency, budget.category, budget.created_at, budget.updated_at,
      ]
    );

    await syncService.enqueueMutation('ledger_budget', budget.id, 'create', budget);
    return budget;
  },
};
