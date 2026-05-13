package com.lifetool.sync;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifetool.sync.dto.SyncChangeDto;
import com.lifetool.sync.dto.SyncConflictDto;
import com.lifetool.sync.dto.SyncMutationDto;
import com.lifetool.sync.dto.SyncMutationResultDto;
import com.lifetool.sync.dto.SyncPullRequest;
import com.lifetool.sync.dto.SyncPullResponse;
import com.lifetool.sync.dto.SyncPushRequest;
import com.lifetool.sync.dto.SyncPushResponse;

@Service
public class SyncService {

    private final ObjectMapper objectMapper;
    private final SyncStore syncStore;

    public SyncService(ObjectMapper objectMapper, SyncStore syncStore) {
        this.objectMapper = objectMapper;
        this.syncStore = syncStore;
    }

    public SyncPushResponse push(String userId, SyncPushRequest request) {
        List<SyncMutationResultDto> applied = new ArrayList<>();
        List<SyncMutationResultDto> rejected = new ArrayList<>();
        List<SyncConflictDto> conflicts = new ArrayList<>();

        for (SyncMutationDto mutation : request.mutations()) {
            if (!isSupportedOperation(mutation.operation())) {
                rejected.add(new SyncMutationResultDto(
                        mutation.mutationId(),
                        mutation.entityType(),
                        mutation.entityId(),
                        null,
                        "UNSUPPORTED_OPERATION",
                        "Unsupported operation: " + mutation.operation()));
                continue;
            }

            var existing = syncStore.find(userId, mutation.entityType(), mutation.entityId());
            if (existing.isEmpty() && ("update".equals(mutation.operation()) || "delete".equals(mutation.operation()))) {
                rejected.add(new SyncMutationResultDto(
                        mutation.mutationId(),
                        mutation.entityType(),
                        mutation.entityId(),
                        null,
                        "ENTITY_NOT_FOUND",
                        "Entity does not exist on server"));
                continue;
            }

            if (mutation.baseVersion() != null && existing.isPresent()
                    && existing.get().serverVersion() != mutation.baseVersion()) {
                SyncEntityRecord serverRecord = existing.get();
                conflicts.add(new SyncConflictDto(
                        mutation.mutationId(),
                        mutation.entityType(),
                        mutation.entityId(),
                        mutation.baseVersion(),
                        serverRecord.serverVersion(),
                        serverRecord.deleted(),
                        serverRecord.payload()));
                continue;
            }

            boolean deleted = "delete".equals(mutation.operation());
            JsonNode payload = deleted
                    ? objectMapper.createObjectNode()
                    : (mutation.payload() == null ? objectMapper.createObjectNode() : mutation.payload());
            SyncEntityRecord saved = syncStore.save(
                    userId,
                    mutation.entityType(),
                    mutation.entityId(),
                    deleted,
                    payload);
            applied.add(new SyncMutationResultDto(
                    mutation.mutationId(),
                    saved.entityType(),
                    saved.entityId(),
                    saved.serverVersion(),
                    null,
                    null));
        }

        return new SyncPushResponse(applied, rejected, conflicts, Long.toString(syncStore.currentVersion()));
    }

    public SyncPullResponse pull(String userId, SyncPullRequest request) {
        long cursor = parseCursor(request.cursor());
        List<SyncChangeDto> changes = syncStore.changesSince(userId, cursor, request.entityTypes()).stream()
                .map(record -> new SyncChangeDto(
                        record.entityType(),
                        record.entityId(),
                        record.serverVersion(),
                        record.deleted(),
                        record.payload()))
                .toList();

        String nextCursor = changes.isEmpty()
                ? Long.toString(syncStore.currentVersion())
                : Long.toString(changes.get(changes.size() - 1).serverVersion());
        return new SyncPullResponse(changes, nextCursor, false);
    }

    private boolean isSupportedOperation(String operation) {
        return "create".equals(operation) || "update".equals(operation) || "delete".equals(operation);
    }

    private long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
