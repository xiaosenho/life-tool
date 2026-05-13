package com.lifetool.sync.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SyncConflictDto(
        String mutationId,
        String entityType,
        String entityId,
        Long baseVersion,
        Long serverVersion,
        Boolean deleted,
        JsonNode payload) {
}
