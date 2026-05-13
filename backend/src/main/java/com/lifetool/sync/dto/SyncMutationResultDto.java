package com.lifetool.sync.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyncMutationResultDto(
        String mutationId,
        String entityType,
        String entityId,
        Long serverVersion,
        String code,
        String message) {
}
