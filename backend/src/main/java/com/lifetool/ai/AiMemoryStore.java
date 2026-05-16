package com.lifetool.ai;

import java.util.List;
import java.util.Optional;

public interface AiMemoryStore {

    AiMemoryItem save(AiMemoryItem memory);

    Optional<AiMemoryItem> findById(String id);

    List<AiMemoryItem> findEnabledByUserId(String userId);
}
