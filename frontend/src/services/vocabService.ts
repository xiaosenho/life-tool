import { apiClient } from './apiClient';

export interface VocabBook {
  code: string;
  name: string;
  variant: string;
  version: string;
  wordCount: number;
}

export interface VocabEntry {
  seqNo: number;
  word: string;
  phonetic: string | null;
  meaningZh: string;
}

export interface VocabPage {
  bookCode: string;
  variant: string;
  bookName: string;
  offset: number;
  limit: number;
  total: number;
  entries: VocabEntry[];
}

export interface VocabProgress {
  bookCode: string;
  variant: string;
  lastSeqNo: number;
  hideMeaning: boolean;
}

export const vocabService = {
  listBooks() {
    return apiClient.get<VocabBook[]>('/vocab/books');
  },
  getPage(bookCode: string, variant = "ordered", offset = 0, limit = 30) {
    const query = new URLSearchParams({ bookCode, variant, offset: String(offset), limit: String(limit) });
    return apiClient.get<VocabPage>(`/vocab/page?${query.toString()}`);
  },
  getProgress(bookCode: string, variant = "ordered") {
    const query = new URLSearchParams({ bookCode, variant });
    return apiClient.get<VocabProgress>(`/vocab/progress?${query.toString()}`);
  },
  updateProgress(bookCode: string, payload: { variant?: string; lastSeqNo?: number; hideMeaning?: boolean }) {
    return apiClient.put<VocabProgress>('/vocab/progress', { bookCode, ...payload });
  },
};
