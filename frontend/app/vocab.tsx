import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Modal, Pressable, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Screen } from '@/components/Screen';
import { colors } from '@/theme/colors';
import { vocabService, VocabBook, VocabEntry } from '@/services/vocabService';
import { useFocusEffect } from 'expo-router';

const PAGE_SIZE = 30;

export default function VocabScreen() {
  const [books, setBooks] = useState<VocabBook[]>([]);
  const [selectedBookCode, setSelectedBookCode] = useState('cet4');
  const [selectedVariant, setSelectedVariant] = useState<'ordered' | 'shuffled'>('ordered');
  const [entries, setEntries] = useState<VocabEntry[]>([]);
  const [offset, setOffset] = useState(0);
  const [total, setTotal] = useState(0);
  const [hideMeaning, setHideMeaning] = useState(false);
  const [loading, setLoading] = useState(false);
  const [currentSeqNo, setCurrentSeqNo] = useState(0);
  const [bookPickerVisible, setBookPickerVisible] = useState(false);
  const hasLoadedBookStateRef = useRef(false);

  const selectedBook = useMemo(
    () => books.find((book) => book.code === selectedBookCode && book.variant === selectedVariant) ?? null,
    [books, selectedBookCode, selectedVariant]
  );

  const loadBooks = useCallback(async () => {
    const response = await vocabService.listBooks();
    if (!response.success || !response.data) {
      throw new Error(response.error?.message || '加载词书失败');
    }
    setBooks(response.data);
    if (!response.data.some((book) => book.code === selectedBookCode && book.variant === selectedVariant) && response.data[0]) {
      setSelectedBookCode(response.data[0].code);
      setSelectedVariant((response.data[0].variant as 'ordered' | 'shuffled') ?? 'ordered');
    }
  }, [selectedBookCode, selectedVariant]);

  const loadPage = useCallback(async (bookCode: string, variant: 'ordered' | 'shuffled', nextOffset: number) => {
    const pageResponse = await vocabService.getPage(bookCode, variant, nextOffset, PAGE_SIZE);
    if (!pageResponse.success || !pageResponse.data) {
      throw new Error(pageResponse.error?.message || '加载单词失败');
    }
    setEntries(pageResponse.data.entries);
    setOffset(pageResponse.data.offset);
    setTotal(pageResponse.data.total);
    return pageResponse.data;
  }, []);

  const loadBookState = useCallback(async (bookCode: string, variant: 'ordered' | 'shuffled') => {
    setLoading(true);
    try {
      const progressResponse = await vocabService.getProgress(bookCode, variant);
      if (!progressResponse.success || !progressResponse.data) {
        throw new Error(progressResponse.error?.message || '加载学习进度失败');
      }
      const nextHideMeaning = progressResponse.data.hideMeaning;
      const nextSeqNo = progressResponse.data.lastSeqNo;
      const nextOffset = progressResponse.data.lastSeqNo > 0
        ? Math.floor((progressResponse.data.lastSeqNo - 1) / PAGE_SIZE) * PAGE_SIZE
        : 0;
      setHideMeaning(nextHideMeaning);
      setCurrentSeqNo(nextSeqNo);
      await loadPage(bookCode, variant, nextOffset);
      hasLoadedBookStateRef.current = true;
    } finally {
      setLoading(false);
    }
  }, [loadPage]);

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
    loadBookState(selectedBookCode, selectedVariant).catch((error) => {
      Alert.alert('加载失败', error instanceof Error ? error.message : '请稍后重试');
    });
  }, [selectedBookCode, selectedVariant, books.length, loadBookState]);

  useFocusEffect(
    useCallback(() => {
      if (!hasLoadedBookStateRef.current) return;
      if (!selectedBookCode || books.length === 0) return;
      loadBookState(selectedBookCode, selectedVariant).catch(() => {});
    }, [selectedBookCode, selectedVariant, loadBookState, books.length])
  );

  const persistProgress = useCallback(async (next: { lastSeqNo?: number; hideMeaning?: boolean }) => {
    const response = await vocabService.updateProgress(selectedBookCode, { variant: selectedVariant, ...next } as any);
    if (!response?.success) {
      throw new Error(response?.error?.message || '保存进度失败');
    }
  }, [selectedBookCode, selectedVariant]);

  const handleToggleHideMeaning = async () => {
    const next = !hideMeaning;
    setHideMeaning(next);
    try {
      await persistProgress({ hideMeaning: next, lastSeqNo: currentSeqNo || offset + 1 });
    } catch (error) {
      setHideMeaning(!next);
      Alert.alert('保存失败', error instanceof Error ? error.message : '请稍后重试');
    }
  };

  const changePage = async (nextOffset: number) => {
    const normalized = Math.max(0, Math.min(nextOffset, Math.max(total - PAGE_SIZE, 0)));
    try {
      await loadPage(selectedBookCode, selectedVariant, normalized);
      const nextSeqNo = normalized + 1;
      setCurrentSeqNo(nextSeqNo);
      await persistProgress({ lastSeqNo: nextSeqNo, hideMeaning });
    } catch (error) {
      Alert.alert('翻页失败', error instanceof Error ? error.message : '请稍后重试');
    }
  };

  const progressRatio = total > 0 ? Math.min(Math.max(currentSeqNo / total, 0), 1) : 0;

  return (
    <Screen title="背单词" scrollable={false}>
      <View style={styles.screenBody}>
        <ScrollView contentContainerStyle={styles.scrollContent}>
          <View style={styles.headerCard}>
        <TouchableOpacity style={styles.selectBox} onPress={() => setBookPickerVisible(true)} activeOpacity={0.85}>
          <View style={styles.selectBoxContent}>
            <Text style={styles.selectLabel}>当前词书</Text>
            <Text style={styles.selectValue}>{selectedBook?.name ?? '请选择词书'}</Text>
          </View>
          <Text style={styles.selectArrow}>▾</Text>
        </TouchableOpacity>
        <View style={styles.metaRow}>
          <Text style={styles.metaText}>{selectedBook?.name ?? '词书'} · {offset + 1}-{Math.min(offset + entries.length, total)}</Text>
        </View>
        <View style={styles.progressSection}>
          <View style={styles.progressHeader}>
            <Text style={styles.progressLabel}>学习进度</Text>
            <Text style={styles.progressText}>{currentSeqNo}/{total || 0}</Text>
          </View>
          <View style={styles.progressTrack}>
            <View style={[styles.progressFill, { width: `${progressRatio * 100}%` }]} />
          </View>
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
                    <View style={styles.wordRow}>
                      <Text style={styles.word}>{entry.word}</Text>
                      {entry.phonetic ? <Text style={styles.phonetic}>{entry.phonetic}</Text> : null}
                    </View>
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
        </ScrollView>

        <TouchableOpacity style={styles.floatingToggleButton} onPress={handleToggleHideMeaning} activeOpacity={0.9}>
          <Text style={styles.floatingToggleButtonText}>{hideMeaning ? '显' : '藏'}</Text>
          <Text style={styles.floatingToggleButtonSubtext}>中文</Text>
        </TouchableOpacity>
      </View>

      <Modal
        visible={bookPickerVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setBookPickerVisible(false)}
      >
        <Pressable style={styles.modalOverlay} onPress={() => setBookPickerVisible(false)}>
          <Pressable style={styles.modalCard} onPress={() => {}}>
            <Text style={styles.modalTitle}>选择词书</Text>
            <ScrollView contentContainerStyle={styles.modalList}>
              {books.map((book) => {
                const active = book.code === selectedBookCode && book.variant === selectedVariant;
                return (
                  <TouchableOpacity
                    key={`${book.code}-${book.variant ?? 'ordered'}`}
                    style={[styles.modalOption, active && styles.modalOptionActive]}
                    onPress={() => {
                      setBookPickerVisible(false);
                      setOffset(0);
                      setSelectedBookCode(book.code);
                      setSelectedVariant((book.variant as 'ordered' | 'shuffled') ?? 'ordered');
                    }}
                  >
                    <View style={styles.modalOptionContent}>
                      <Text style={[styles.modalOptionText, active && styles.modalOptionTextActive]}>{book.name}</Text>
                      <Text style={[styles.modalOptionMeta, active && styles.modalOptionTextActive]}>
                        {book.wordCount} 词
                      </Text>
                    </View>
                    {active ? <Text style={styles.modalCheck}>✓</Text> : null}
                  </TouchableOpacity>
                );
              })}
            </ScrollView>
          </Pressable>
        </Pressable>
      </Modal>
    </Screen>
  );
}

const styles = StyleSheet.create({
  screenBody: {
    flex: 1,
    position: 'relative',
  },
  scrollContent: {
    paddingBottom: 120,
  },
  headerCard: {
    backgroundColor: colors.surface,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 14,
    marginBottom: 14,
  },
  selectBox: {
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.background,
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 12,
  },
  selectBoxContent: {
    flex: 1,
  },
  selectLabel: {
    color: colors.muted,
    fontSize: 12,
    marginBottom: 4,
  },
  selectValue: {
    color: colors.text,
    fontSize: 16,
    fontWeight: '700',
  },
  selectArrow: {
    color: colors.muted,
    fontSize: 18,
    fontWeight: '700',
  },
  progressSection: {
    marginTop: 12,
  },
  progressHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  progressLabel: {
    color: colors.muted,
    fontSize: 13,
    fontWeight: '600',
  },
  progressText: {
    color: colors.text,
    fontSize: 13,
    fontWeight: '700',
  },
  progressTrack: {
    height: 10,
    borderRadius: 999,
    backgroundColor: colors.background,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    borderRadius: 999,
    backgroundColor: colors.accent,
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
  wordRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  word: {
    color: colors.text,
    fontSize: 18,
    fontWeight: '800',
    flex: 1,
  },
  phonetic: {
    color: colors.accent,
    fontSize: 13,
    textAlign: 'right',
    maxWidth: '45%',
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
  floatingToggleButton: {
    position: 'absolute',
    right: 22,
    bottom: 34,
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: colors.text,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.16,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 6 },
    elevation: 6,
  },
  floatingToggleButtonText: {
    color: colors.surface,
    fontSize: 18,
    fontWeight: '800',
    lineHeight: 22,
  },
  floatingToggleButtonSubtext: {
    color: `${colors.surface}CC`,
    fontSize: 11,
    fontWeight: '600',
    lineHeight: 14,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.32)',
    justifyContent: 'center',
    padding: 20,
  },
  modalCard: {
    backgroundColor: colors.surface,
    borderRadius: 18,
    padding: 16,
    maxHeight: '72%',
  },
  modalTitle: {
    color: colors.text,
    fontSize: 17,
    fontWeight: '800',
    marginBottom: 12,
  },
  modalList: {
    gap: 10,
  },
  modalOption: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  modalOptionActive: {
    borderColor: colors.accent,
    backgroundColor: `${colors.accent}12`,
  },
  modalOptionContent: {
    flex: 1,
  },
  modalOptionText: {
    color: colors.text,
    fontSize: 15,
    fontWeight: '700',
  },
  modalOptionMeta: {
    color: colors.muted,
    fontSize: 12,
    marginTop: 4,
  },
  modalOptionTextActive: {
    color: colors.accent,
  },
  modalCheck: {
    color: colors.accent,
    fontSize: 18,
    fontWeight: '800',
  },
});
