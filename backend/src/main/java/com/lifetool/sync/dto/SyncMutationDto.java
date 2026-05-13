package com.lifetool.sync.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;

public record SyncMutationDto(
        @NotBlank String mutationId,
        @NotBlank String entityType,
        @NotBlank String entityId,
        @NotBlank String operation,
        Long baseVersion,
        JsonNode payload) {
}
