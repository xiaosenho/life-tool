import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Alert, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Screen } from '@/components/Screen';
import { colors } from '@/theme/colors';
import { vocabService, VocabBook, VocabEntry } from '@/services/vocabService';
import { useFocusEffect } from 'expo-router';

const PAGE_SIZE = 30;

export default function VocabScreen() {
  const [books, setBooks] = useState<VocabBook[]>([]);
  const [selectedBookCode, setSelectedBookCode] = useState('cet4');
  const [entries, setEntries] = useState<VocabEntry[]>([]);
  const [offset, setOffset] = useState(0);
  const [total, setTotal] = useState(0);
  const [hideMeaning, setHideMeaning] = useState(false);
  const [loading, setLoading] = useState(false);

  const selectedBook = useMemo(
    () => books.find((book) => book.code === selectedBookCode) ?? null,
    [books, selectedBookCode]
  );

  const loadBooks = useCallback(async () => {
    const response = await vocabService.listBooks();
    if (!response.success || !response.data) {
      throw new Error(response.error?.message || '加载词书失败');
    }
    setBooks(response.data);
    if (!response.data.some((book) => book.code === selectedBookCode) && response.data[0]) {
      setSelectedBookCode(response.data[0].code);
    }
  }, [selectedBookCode]);

  const loadPage = useCallback(async (bookCode: string, nextOffset: number) => {
    setLoading(true);
    try {
      const [pageResponse, progressResponse] = await Promise.all([
        vocabService.getPage(bookCode, nextOffset, PAGE_SIZE),
        vocabService.getProgress(bookCode),
      ]);
      if (!pageResponse.success || !pageResponse.data) {
        throw new Error(pageResponse.error?.message || '加载单词失败');
      }
      setEntries(pageResponse.data.entries);
      setOffset(pageResponse.data.offset);
      setTotal(pageResponse.data.total);
      if (progressResponse.success && progressResponse.data) {
        setHideMeaning(progressResponse.data.hideMeaning);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  const bootstrap = useCallback(async () => {
    try {
      await loadBooks();
    } catch (error) {
      Alert.alert('加载失败', error instanceof Error ? error.message : '请稍后重试');
    }
  }, [loadBooks]);

  useEffect(() => {
    bootstrap();
  }, [bootstrap]);

  useEffect(() => {
    if (!selectedBookCode || books.length === 0) return;
    loadPage(selectedBookCode, 0).catch((error) => {
      Alert.alert('加载失败', error instanceof Error ? error.message : '请稍后重试');
    });
  }, [selectedBookCode, books.length, loadPage]);

  useFocusEffect(
    useCallback(() => {
      if (!selectedBookCode || books.length === 0) return;
      loadPage(selectedBookCode, offset).catch(() => {});
    }, [selectedBookCode, offset, loadPage, books.length])
  );

  const persistProgress = useCallback(async (next: { lastSeqNo?: number; hideMeaning?: boolean }) => {
    const response = await vocabService.updateProgress(selectedBookCode, next as any);
    if (!response?.success) {
      throw new Error(response?.error?.message || '保存进度失败');
    }
  }, [selectedBookCode]);

  const handleToggleHideMeaning = async () => {
    const next = !hideMeaning;
    setHideMeaning(next);
    try {
      await persistProgress({ hideMeaning: next, lastSeqNo: offset + 1 });
    } catch (error) {
      setHideMeaning(!next);
      Alert.alert('保存失败', error instanceof Error ? error.message : '请稍后重试');
    }
  };

  const changePage = async (nextOffset: number) => {
    const normalized = Math.max(0, Math.min(nextOffset, Math.max(total - PAGE_SIZE, 0)));
    try {
      await loadPage(selectedBookCode, normalized);
      await persistProgress({ lastSeqNo: normalized + 1, hideMeaning });
    } catch (error) {
      Alert.alert('翻页失败', error instanceof Error ? error.message : '请稍后重试');
    }
  };

  return (
    <Screen title="背单词">
      <View style={styles.headerCard}>
        <View style={styles.bookTabs}>
          {books.map((book) => {
            const active = book.code === selectedBookCode;
            return (
              <TouchableOpacity
                key={book.code}
                style={[styles.bookTab, active && styles.bookTabActive]}
                onPress={() => {
                  setOffset(0);
                  setSelectedBookCode(book.code);
                }}
              >
                <Text style={[styles.bookTabText, active && styles.bookTabTextActive]}>{book.name}</Text>
              </TouchableOpacity>
            );
          })}
        </View>
        <View style={styles.metaRow}>
          <Text style={styles.metaText}>{selectedBook?.name ?? '词书'} · {offset + 1}-{Math.min(offset + entries.length, total)}</Text>
          <TouchableOpacity style={styles.toggleButton} onPress={handleToggleHideMeaning}>
            <Text style={styles.toggleButtonText}>{hideMeaning ? '显示中文' : '隐藏中文'}</Text>
          </TouchableOpacity>
        </View>
      </View>

      {loading ? (
        <View style={styles.loadingWrap}>
          <ActivityIndicator color={colors.accent} />
          <Text style={styles.loadingText}>正在加载单词…</Text>
        </View>
      ) : (
        <View style={styles.listCard}>
          {entries.map((entry) => (
            <View key={`${selectedBookCode}-${entry.seqNo}`} style={styles.entryRow}>
              <View style={styles.entryIndexWrap}>
                <Text style={styles.entryIndex}>{entry.seqNo}</Text>
              </View>
              <View style={styles.entryContent}>
                <Text style={styles.word}>{entry.word}</Text>
                {entry.phonetic ? <Text style={styles.phonetic}>{entry.phonetic}</Text> : null}
                {!hideMeaning ? <Text style={styles.meaning}>{entry.meaningZh}</Text> : null}
              </View>
            </View>
          ))}
        </View>
      )}

      <View style={styles.footerActions}>
        <TouchableOpacity
          style={[styles.pageButton, offset === 0 && styles.pageButtonDisabled]}
          disabled={offset === 0 || loading}
          onPress={() => changePage(offset - PAGE_SIZE)}
        >
          <Text style={styles.pageButtonText}>上一组30词</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.pageButton, offset + PAGE_SIZE >= total && styles.pageButtonDisabled]}
          disabled={offset + PAGE_SIZE >= total || loading}
          onPress={() => changePage(offset + PAGE_SIZE)}
        >
          <Text style={styles.pageButtonText}>下一组30词</Text>
        </TouchableOpacity>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  headerCard: {
    backgroundColor: colors.surface,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 14,
    marginBottom: 14,
  },
  bookTabs: {
    flexDirection: 'row',
    gap: 8,
    flexWrap: 'wrap',
    marginBottom: 12,
  },
  bookTab: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.background,
  },
  bookTabActive: {
    backgroundColor: colors.accent,
    borderColor: colors.accent,
  },
  bookTabText: {
    color: colors.text,
    fontSize: 14,
    fontWeight: '600',
  },
  bookTabTextActive: {
    color: colors.surface,
  },
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 12,
  },
  metaText: {
    color: colors.muted,
    fontSize: 13,
    flex: 1,
  },
  toggleButton: {
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: colors.text,
  },
  toggleButtonText: {
    color: colors.surface,
    fontWeight: '600',
    fontSize: 13,
  },
  loadingWrap: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 48,
  },
  loadingText: {
    marginTop: 10,
    color: colors.muted,
  },
  listCard: {
    backgroundColor: colors.surface,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
  },
  entryRow: {
    flexDirection: 'row',
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
    gap: 12,
  },
  entryIndexWrap: {
    width: 28,
    alignItems: 'center',
    paddingTop: 2,
  },
  entryIndex: {
    color: colors.muted,
    fontSize: 12,
    fontWeight: '700',
  },
  entryContent: {
    flex: 1,
  },
  word: {
    color: colors.text,
    fontSize: 18,
    fontWeight: '800',
  },
  phonetic: {
    marginTop: 4,
    color: colors.accent,
    fontSize: 13,
  },
  meaning: {
    marginTop: 6,
    color: colors.text,
    fontSize: 15,
    lineHeight: 22,
  },
  footerActions: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 14,
    marginBottom: 24,
  },
  pageButton: {
    flex: 1,
    backgroundColor: colors.accent,
    borderRadius: 12,
    paddingVertical: 12,
    alignItems: 'center',
  },
  pageButtonDisabled: {
    opacity: 0.4,
  },
  pageButtonText: {
    color: colors.surface,
    fontWeight: '700',
  },
});
