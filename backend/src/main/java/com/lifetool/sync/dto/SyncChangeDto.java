package com.lifetool.sync.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SyncChangeDto(
        String entityType,
        String entityId,
        Long serverVersion,
        Boolean deleted,
        JsonNode payload) {
}
