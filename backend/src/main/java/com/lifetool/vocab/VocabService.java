package com.lifetool.vocab;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifetool.vocab.dto.UpdateVocabProgressRequest;
import com.lifetool.vocab.dto.VocabBookResponse;
import com.lifetool.vocab.dto.VocabEntryResponse;
import com.lifetool.vocab.dto.VocabPageResponse;
import com.lifetool.vocab.dto.VocabProgressResponse;

@Service
public class VocabService {
    private static final Logger log = LoggerFactory.getLogger(VocabService.class);
    private static final int DEFAULT_LIMIT = 30;

    private final VocabStore store;
    private final ObjectMapper objectMapper;

    public VocabService(VocabStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public List<VocabBookResponse> listBooks() {
        return store.listBooks().stream().map(VocabBookResponse::from).toList();
    }

    public VocabPageResponse getPage(String bookCode, int offset, int limit) {
        VocabBook book = findBook(bookCode);
        int normalizedLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 100);
        int normalizedOffset = Math.max(offset, 0);
        List<VocabEntryResponse> entries = store.listEntries(book.getId(), normalizedOffset, normalizedLimit).stream()
                .map(VocabEntryResponse::from)
                .toList();
        return new VocabPageResponse(book.getCode(), book.getName(), normalizedOffset, normalizedLimit, book.getWordCount(), entries);
    }

    public VocabProgressResponse getProgress(String userId, String bookCode) {
        VocabBook book = findBook(bookCode);
        return store.findProgress(userId, book.getId())
                .map(progress -> new VocabProgressResponse(bookCode, progress.getLastSeqNo(), progress.isHideMeaning()))
                .orElse(new VocabProgressResponse(bookCode, 0, false));
    }

    public VocabProgressResponse updateProgress(String userId, UpdateVocabProgressRequest request) {
        if (request.bookCode() == null || request.bookCode().isBlank()) {
            throw new VocabException("VALIDATION_ERROR", "bookCode is required");
        }
        VocabBook book = findBook(request.bookCode());
        UserVocabProgress progress = store.findProgress(userId, book.getId()).orElseGet(UserVocabProgress::new);
        progress.setUserId(userId);
        progress.setBookId(book.getId());
        progress.setLastSeqNo(Math.max(0, request.lastSeqNo() == null ? progress.getLastSeqNo() : request.lastSeqNo()));
        progress.setHideMeaning(request.hideMeaning() == null ? progress.isHideMeaning() : request.hideMeaning());
        UserVocabProgress saved = store.saveProgress(progress);
        return new VocabProgressResponse(book.getCode(), saved.getLastSeqNo(), saved.isHideMeaning());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedBooksIfNeeded() {
        if (store.hasAnyBooks()) {
            return;
        }
        try {
            List<VocabStore.VocabBookSeed> seeds = new ArrayList<>();
            seeds.add(loadSeed("cet4", "英语四级", "vocab/cet4.json"));
            seeds.add(loadSeed("cet6", "英语六级", "vocab/cet6.json"));
            seeds.add(loadSeed("kaoyan", "考研英语", "vocab/kaoyan.json"));
            store.replaceBookData(seeds);
            log.info("Seeded vocab books successfully, count={}", seeds.size());
        } catch (Exception ex) {
            log.warn("Failed to seed vocab books", ex);
        }
    }

    private VocabStore.VocabBookSeed loadSeed(String code, String name, String path) throws Exception {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            List<Map<String, Object>> raw = objectMapper.readValue(inputStream, new TypeReference<>() {});
            List<VocabStore.VocabEntrySeed> entries = new ArrayList<>();
            int seq = 1;
            for (Map<String, Object> item : raw) {
                String word = text(item, "word", "name", "headWord");
                String meaning = text(item, "meaningZh", "trans", "desc", "meaning");
                if ((meaning == null || meaning.isBlank()) && item.get("translations") instanceof List<?> translations && !translations.isEmpty()) {
                    meaning = translations.stream()
                            .filter(Map.class::isInstance)
                            .map(Map.class::cast)
                            .map(entry -> {
                                Object translation = entry.get("translation");
                                Object type = entry.get("type");
                                String body = translation == null ? null : String.valueOf(translation).trim();
                                if (body == null || body.isBlank()) {
                                    return null;
                                }
                                String prefix = type == null ? "" : String.valueOf(type).trim();
                                return prefix.isBlank() ? body : prefix + ". " + body;
                            })
                            .filter(value -> value != null && !value.isBlank())
                            .reduce((a, b) -> a + "；" + b)
                            .orElse(null);
                }
                if (word == null || word.isBlank() || meaning == null || meaning.isBlank()) {
                    continue;
                }
                String phonetic = text(item, "phonetic", "usphone", "ukphone");
                entries.add(new VocabStore.VocabEntrySeed(seq++, word.trim(), blankToNull(phonetic), meaning.trim()));
            }
            return new VocabStore.VocabBookSeed(code, name, "2026.1", entries);
        }
    }

    private String text(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value == null) continue;
            if (value instanceof String str && !str.isBlank()) return str;
            if (value instanceof List<?> list && !list.isEmpty()) {
                String joined = list.stream().map(String::valueOf).filter(s -> !s.isBlank()).reduce((a, b) -> a + "；" + b).orElse(null);
                if (joined != null && !joined.isBlank()) return joined;
            }
            if (value instanceof Map<?, ?> map && !map.isEmpty()) {
                Map<String, Object> ordered = new LinkedHashMap<>();
                map.forEach((k, v) -> ordered.put(String.valueOf(k), v));
                String joined = ordered.values().stream().map(String::valueOf).filter(s -> !s.isBlank()).reduce((a, b) -> a + "；" + b).orElse(null);
                if (joined != null && !joined.isBlank()) return joined;
            }
            String text = String.valueOf(value);
            if (!text.isBlank()) return text;
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private VocabBook findBook(String code) {
        return store.findBookByCode(code)
                .orElseThrow(() -> new VocabException("NOT_FOUND", "词书不存在: " + code));
    }
}
