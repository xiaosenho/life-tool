package com.lifetool.sync;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

public interface SyncStore {
    Optional<SyncEntityRecord> find(String userId, String entityType, String entityId);

    SyncEntityRecord save(String userId, String entityType, String entityId, boolean deleted,
                          JsonNode payload);

    List<SyncEntityRecord> changesSince(String userId, long cursor, List<String> entityTypes);

    long currentVersion();
}
