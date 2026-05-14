package com.lifetool.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class AiMemoryStore {
    private final Map<String, AiMemoryItem> memoriesById = new ConcurrentHashMap<>();

    public AiMemoryItem save(AiMemoryItem memory) {
        memoriesById.put(memory.getId(), memory);
        return memory;
    }

    public Optional<AiMemoryItem> findById(String id) {
        return Optional.ofNullable(memoriesById.get(id));
    }

    public List<AiMemoryItem> findEnabledByUserId(String userId) {
        return memoriesById.values().stream()
                .filter(memory -> memory.getUserId().equals(userId))
                .filter(AiMemoryItem::isEnabled)
                .filter(memory -> memory.getDeletedAt() == null)
                .sorted(Comparator.comparing(AiMemoryItem::getCreatedAt).reversed())
                .toList();
    }
}
