package com.lifetool.sync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

@Repository
public class SyncStore {

    private final AtomicLong versionSequence = new AtomicLong(0);
    private final Map<String, Map<String, SyncEntityRecord>> recordsByUser = new ConcurrentHashMap<>();

    public Optional<SyncEntityRecord> find(String userId, String entityType, String entityId) {
        return Optional.ofNullable(recordsByUser.getOrDefault(userId, Map.of()).get(key(entityType, entityId)));
    }

    public SyncEntityRecord save(String userId, String entityType, String entityId, boolean deleted,
                                 com.fasterxml.jackson.databind.JsonNode payload) {
        long nextVersion = versionSequence.incrementAndGet();
        SyncEntityRecord record = new SyncEntityRecord(
                userId,
                entityType,
                entityId,
                nextVersion,
                deleted,
                payload);
        recordsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .put(key(entityType, entityId), record);
        return record;
    }

    public List<SyncEntityRecord> changesSince(String userId, long cursor, List<String> entityTypes) {
        List<SyncEntityRecord> records = new ArrayList<>(recordsByUser.getOrDefault(userId, Map.of()).values());
        return records.stream()
                .filter(record -> record.serverVersion() > cursor)
                .filter(record -> entityTypes == null || entityTypes.isEmpty() || entityTypes.contains(record.entityType()))
                .sorted(Comparator.comparingLong(SyncEntityRecord::serverVersion))
                .toList();
    }

    public long currentVersion() {
        return versionSequence.get();
    }

    private String key(String entityType, String entityId) {
        return entityType + ":" + entityId;
    }
}
