package com.lifetool.vocab;

import java.util.List;
import java.util.Optional;

public interface VocabStore {
    List<VocabBook> listBooks();
    Optional<VocabBook> findBookByCodeAndVariant(String code, String variant);
    List<VocabEntry> listEntries(String bookId, int offset, int limit);
    Optional<UserVocabProgress> findProgress(String userId, String bookId);
    UserVocabProgress saveProgress(UserVocabProgress progress);
    boolean hasAnyBooks();
    void replaceBookData(List<VocabBookSeed> books);

    record VocabBookSeed(String code, String variant, String name, String version, List<VocabEntrySeed> entries) {}
    record VocabEntrySeed(int seqNo, String word, String phonetic, String meaningZh) {}
}
