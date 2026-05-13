import { apiClient } from './apiClient';
import { syncMutationRepository } from '@/db/syncMutationRepository';
import { syncStateRepository } from '@/db/syncStateRepository';
import { createLocalId } from '@/utils/id';

export interface SyncPushRequest {
  deviceId: string;
  clientSeq: number;
  mutations: {
    mutationId: string;
    entityType: string;
    entityId: string;
    operation: string;
    baseVersion: number | null;
    payload: any;
  }[];
}

export interface SyncPushResponse {
  applied: {
    mutationId: string;
    entityType: string;
    entityId: string;
    serverVersion: number;
  }[];
  rejected: any[];
  conflicts: any[];
  serverCursor: string;
}

export interface SyncPullRequest {
  deviceId: string;
  cursor: string | null;
  entityTypes: string[];
}

export interface SyncPullResponse {
  changes: {
    entityType: string;
    entityId: string;
    serverVersion: number;
    deleted: boolean;
    payload: any;
  }[];
  nextCursor: string;
  hasMore: boolean;
}

export const syncService = {
  async getDeviceId() {
    let deviceId = await syncStateRepository.getValue('device_id');
    if (!deviceId) {
      deviceId = createLocalId('device');
      await syncStateRepository.setValue('device_id', deviceId);
    }
    return deviceId;
  },

  async enqueueMutation(entityType: string, entityId: string, operation: 'create' | 'update' | 'delete', payload: any) {
    const mutationId = createLocalId('mutation');
    await syncMutationRepository.enqueue({
      mutation_id: mutationId,
      entity_type: entityType,
      entity_id: entityId,
      operation: operation,
      payload: JSON.stringify(payload),
    });
    // In a real app, we might trigger a background sync here
  },

  async pushPendingMutations() {
    const pending = await syncMutationRepository.getPending();
    if (pending.length === 0) return true;

    const deviceId = await this.getDeviceId();
    const clientSeq = parseInt(await syncStateRepository.getValue('client_seq') || '0', 10) + 1;

    const request: SyncPushRequest = {
      deviceId,
      clientSeq,
      mutations: pending.map(p => ({
        mutationId: p.mutation_id,
        entityType: p.entity_type,
        entityId: p.entity_id,
        operation: p.operation,
        baseVersion: null, // Simplified for MVP
        payload: JSON.parse(p.payload),
      })),
    };

    const response = await apiClient.post<SyncPushResponse>('/sync/push', request);

    if (response.success && response.data) {
      const appliedIds = response.data.applied.map(a => a.mutationId);
      await syncMutationRepository.markAsSynced(appliedIds);
      await syncStateRepository.setValue('client_seq', clientSeq.toString());
      return true;
    }

    return false;
  },

  async pullChanges() {
    const deviceId = await this.getDeviceId();
    const cursor = await syncStateRepository.getValue('last_cursor');

    const request: SyncPullRequest = {
      deviceId,
      cursor,
      entityTypes: ['focus_session', 'habit', 'habit_checkin', 'privacy_setting', 'task'],
    };

    const response = await apiClient.post<SyncPullResponse>('/sync/pull', request);

    if (response.success && response.data) {
      // Here we would apply changes to local tables
      // For MVP, we just update the cursor
      await syncStateRepository.setValue('last_cursor', response.data.nextCursor);
      await syncStateRepository.setValue('last_sync_time', new Date().toISOString());
      
      // TODO: Apply changes to business tables
      return true;
    }

    return false;
  },

  async runSync() {
    try {
      const pushed = await this.pushPendingMutations();
      const pulled = await this.pullChanges();
      return pushed && pulled;
    } catch (error) {
      console.error('Sync failed:', error);
      return false;
    }
  }
};
