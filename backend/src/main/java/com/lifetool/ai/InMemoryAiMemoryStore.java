package com.lifetool.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryAiMemoryStore implements AiMemoryStore {
    private final Map<String, AiMemoryItem> memoriesById = new ConcurrentHashMap<>();

    @Override
    public AiMemoryItem save(AiMemoryItem memory) {
        memoriesById.put(memory.getId(), memory);
        return memory;
    }

    @Override
    public Optional<AiMemoryItem> findById(String id) {
        AiMemoryItem memory = memoriesById.get(id);
        if (memory == null || memory.getDeletedAt() != null) {
            return Optional.empty();
        }
        return Optional.of(memory);
    }

    @Override
    public List<AiMemoryItem> findEnabledByUserId(String userId) {
        return memoriesById.values().stream()
                .filter(memory -> memory.getUserId().equals(userId))
                .filter(AiMemoryItem::isEnabled)
                .filter(memory -> memory.getDeletedAt() == null)
                .sorted(Comparator.comparing(AiMemoryItem::getCreatedAt).reversed())
                .toList();
    }
}
