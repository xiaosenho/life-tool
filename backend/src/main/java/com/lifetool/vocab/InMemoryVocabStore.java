package com.lifetool.vocab;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryVocabStore implements VocabStore {
    private final List<VocabBook> books = new ArrayList<>();
    private final Map<String, List<VocabEntry>> entriesByBookId = new HashMap<>();
    private final Map<String, UserVocabProgress> progressMap = new HashMap<>();

    @Override
    public List<VocabBook> listBooks() {
        return new ArrayList<>(books);
    }

    @Override
    public Optional<VocabBook> findBookByCodeAndVariant(String code, String variant) {
        return books.stream().filter(book -> book.getCode().equals(code) && book.getVariant().equals(variant)).findFirst();
    }

    @Override
    public List<VocabEntry> listEntries(String bookId, int offset, int limit) {
        List<VocabEntry> all = entriesByBookId.getOrDefault(bookId, List.of());
        int start = Math.min(Math.max(0, offset), all.size());
        int end = Math.min(start + Math.max(1, limit), all.size());
        return new ArrayList<>(all.subList(start, end));
    }

    @Override
    public Optional<UserVocabProgress> findProgress(String userId, String bookId) {
        return Optional.ofNullable(progressMap.get(userId + ":" + bookId));
    }

    @Override
    public UserVocabProgress saveProgress(UserVocabProgress progress) {
        if (progress.getId() == null) progress.setId(UUID.randomUUID().toString());
        Instant now = Instant.now();
        if (progress.getCreatedAt() == null) progress.setCreatedAt(now);
        progress.setUpdatedAt(now);
        progressMap.put(progress.getUserId() + ":" + progress.getBookId(), progress);
        return progress;
    }

    @Override
    public boolean hasAnyBooks() {
        return !books.isEmpty();
    }

    @Override
    public void replaceBookData(List<VocabBookSeed> seeds) {
        books.clear();
        entriesByBookId.clear();
        Instant now = Instant.now();
        for (VocabBookSeed seed : seeds) {
            VocabBook book = new VocabBook();
            book.setId(UUID.randomUUID().toString());
            book.setCode(seed.code());
            book.setVariant(seed.variant());
            book.setName(seed.name());
            book.setVersion(seed.version());
            book.setWordCount(seed.entries().size());
            book.setCreatedAt(now);
            book.setUpdatedAt(now);
            books.add(book);
            List<VocabEntry> entries = new ArrayList<>();
            for (VocabEntrySeed source : seed.entries()) {
                VocabEntry entry = new VocabEntry();
                entry.setId(UUID.randomUUID().toString());
                entry.setBookId(book.getId());
                entry.setSeqNo(source.seqNo());
                entry.setWord(source.word());
                entry.setPhonetic(source.phonetic());
                entry.setMeaningZh(source.meaningZh());
                entry.setCreatedAt(now);
                entry.setUpdatedAt(now);
                entries.add(entry);
            }
            entriesByBookId.put(book.getId(), entries);
        }
    }
}
