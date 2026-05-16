package com.lifetool.vocab;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final Map<String, String> phoneticFallbackByBookCode = new LinkedHashMap<>();

    public VocabService(VocabStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public List<VocabBookResponse> listBooks() {
        return store.listBooks().stream().map(VocabBookResponse::from).toList();
    }

    public VocabPageResponse getPage(String bookCode, String variant, int offset, int limit) {
        VocabBook book = findBook(bookCode, normalizeVariant(variant));
        int normalizedLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 100);
        int normalizedOffset = Math.max(offset, 0);
        List<VocabEntryResponse> entries = store.listEntries(book.getId(), normalizedOffset, normalizedLimit).stream()
                .map(entry -> VocabEntryResponse.from(entry, resolvePhonetic(book.getCode(), entry.getWord(), entry.getPhonetic())))
                .toList();
        return new VocabPageResponse(book.getCode(), book.getVariant(), book.getName(), normalizedOffset, normalizedLimit, book.getWordCount(), entries);
    }

    public VocabProgressResponse getProgress(String userId, String bookCode, String variant) {
        VocabBook book = findBook(bookCode, normalizeVariant(variant));
        return store.findProgress(userId, book.getId())
                .map(progress -> new VocabProgressResponse(bookCode, book.getVariant(), progress.getLastSeqNo(), progress.isHideMeaning()))
                .orElse(new VocabProgressResponse(bookCode, book.getVariant(), 0, false));
    }

    public VocabProgressResponse updateProgress(String userId, UpdateVocabProgressRequest request) {
        if (request.bookCode() == null || request.bookCode().isBlank()) {
            throw new VocabException("VALIDATION_ERROR", "bookCode is required");
        }
        VocabBook book = findBook(request.bookCode(), normalizeVariant(request.variant()));
        UserVocabProgress progress = store.findProgress(userId, book.getId()).orElseGet(UserVocabProgress::new);
        progress.setUserId(userId);
        progress.setBookId(book.getId());
        progress.setLastSeqNo(Math.max(0, request.lastSeqNo() == null ? progress.getLastSeqNo() : request.lastSeqNo()));
        progress.setHideMeaning(request.hideMeaning() == null ? progress.isHideMeaning() : request.hideMeaning());
        UserVocabProgress saved = store.saveProgress(progress);
        return new VocabProgressResponse(book.getCode(), book.getVariant(), saved.getLastSeqNo(), saved.isHideMeaning());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedBooksIfNeeded() {
        if (store.hasAnyBooks()) {
            return;
        }
        try {
            List<VocabStore.VocabBookSeed> seeds = new ArrayList<>();
            VocabStore.VocabBookSeed cet4Ordered = loadSeed("cet4", "ordered", "英语四级", "vocab/cet4.json");
            VocabStore.VocabBookSeed cet6Ordered = loadSeed("cet6", "ordered", "英语六级", "vocab/cet6.json");
            VocabStore.VocabBookSeed kaoyanOrdered = loadSeed("kaoyan", "ordered", "考研英语", "vocab/kaoyan.json");
            seeds.add(cet4Ordered);
            seeds.add(loadSeed("cet4", "shuffled", "英语四级（乱序）", "vocab/cet4-shuffled.txt", indexPhonetics(cet4Ordered.entries())));
            seeds.add(cet6Ordered);
            seeds.add(loadSeed("cet6", "shuffled", "英语六级（乱序）", "vocab/cet6-shuffled.txt", indexPhonetics(cet6Ordered.entries())));
            seeds.add(kaoyanOrdered);
            seeds.add(loadSeed("kaoyan", "shuffled", "考研英语（乱序）", "vocab/kaoyan-shuffled.txt", indexPhonetics(kaoyanOrdered.entries())));
            phoneticFallbackByBookCode.put("cet4", encodePhoneticIndex(indexPhonetics(cet4Ordered.entries())));
            phoneticFallbackByBookCode.put("cet6", encodePhoneticIndex(indexPhonetics(cet6Ordered.entries())));
            phoneticFallbackByBookCode.put("kaoyan", encodePhoneticIndex(indexPhonetics(kaoyanOrdered.entries())));
            store.replaceBookData(seeds);
            log.info("Seeded vocab books successfully, count={}", seeds.size());
        } catch (Exception ex) {
            log.warn("Failed to seed vocab books", ex);
        }
    }

    private VocabStore.VocabBookSeed loadSeed(String code, String variant, String name, String path) throws Exception {
        return loadSeed(code, variant, name, path, Map.of());
    }

    private VocabStore.VocabBookSeed loadSeed(String code, String variant, String name, String path, Map<String, String> phoneticByWord) throws Exception {
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            if (path.endsWith(".txt")) {
                return new VocabStore.VocabBookSeed(code, variant, name, "2026.1", loadTxtEntries(inputStream, phoneticByWord));
            }
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
            return new VocabStore.VocabBookSeed(code, variant, name, "2026.1", entries);
        }
    }

    private List<VocabStore.VocabEntrySeed> loadTxtEntries(InputStream inputStream, Map<String, String> phoneticByWord) throws Exception {
        List<VocabStore.VocabEntrySeed> entries = new ArrayList<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            int seq = 1;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", 2);
                if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    continue;
                }
                String word = parts[0].trim();
                entries.add(new VocabStore.VocabEntrySeed(
                        seq++,
                        word,
                        blankToNull(phoneticByWord.get(normalizeWord(word))),
                        parts[1].trim()));
            }
        }
        return entries;
    }

    private Map<String, String> indexPhonetics(List<VocabStore.VocabEntrySeed> entries) {
        return entries.stream()
                .filter(entry -> entry.word() != null && !entry.word().isBlank())
                .filter(entry -> entry.phonetic() != null && !entry.phonetic().isBlank())
                .collect(java.util.stream.Collectors.toMap(
                        entry -> normalizeWord(entry.word()),
                        VocabStore.VocabEntrySeed::phonetic,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
    }

    private String encodePhoneticIndex(Map<String, String> index) {
        try {
            return objectMapper.writeValueAsString(index);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String resolvePhonetic(String bookCode, String word, String phonetic) {
        if (phonetic != null && !phonetic.isBlank()) {
            return phonetic;
        }
        try {
            String encodedIndex = phoneticFallbackByBookCode.computeIfAbsent(bookCode, this::loadPhoneticFallback);
            if (encodedIndex == null || encodedIndex.isBlank()) {
                return null;
            }
            Map<String, String> decoded = objectMapper.readValue(encodedIndex, new TypeReference<Map<String, String>>() {});
            return blankToNull(decoded.get(normalizeWord(word)));
        } catch (Exception ex) {
            return null;
        }
    }

    private String loadPhoneticFallback(String bookCode) {
        String path = switch (bookCode) {
            case "cet4" -> "vocab/cet4.json";
            case "cet6" -> "vocab/cet6.json";
            case "kaoyan" -> "vocab/kaoyan.json";
            default -> null;
        };
        if (path == null) {
            return "{}";
        }
        try (InputStream inputStream = new ClassPathResource(path).getInputStream()) {
            List<Map<String, Object>> raw = objectMapper.readValue(inputStream, new TypeReference<>() {});
            List<VocabStore.VocabEntrySeed> entries = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                String word = text(item, "word", "name", "headWord");
                if (word == null || word.isBlank()) {
                    continue;
                }
                String phonetic = text(item, "phonetic", "usphone", "ukphone");
                entries.add(new VocabStore.VocabEntrySeed(0, word.trim(), blankToNull(phonetic), ""));
            }
            return encodePhoneticIndex(indexPhonetics(entries));
        } catch (Exception ex) {
            log.warn("Failed to load phonetic fallback for {}", bookCode, ex);
            return "{}";
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

    private String normalizeWord(String word) {
        return word == null ? "" : word.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeVariant(String variant) {
        return (variant == null || variant.isBlank()) ? "ordered" : variant.trim();
    }

    private VocabBook findBook(String code, String variant) {
        return store.findBookByCodeAndVariant(code, variant)
                .orElseThrow(() -> new VocabException("NOT_FOUND", "词书不存在: " + code + " / " + variant));
    }
}
