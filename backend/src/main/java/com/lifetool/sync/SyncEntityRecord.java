package com.lifetool.sync;

import com.fasterxml.jackson.databind.JsonNode;

public record SyncEntityRecord(
        String userId,
        String entityType,
        String entityId,
        long serverVersion,
        boolean deleted,
        JsonNode payload) {
}
