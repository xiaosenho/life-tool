package com.lifetool.sync.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SyncPushRequest(
        @NotBlank String deviceId,
        Long clientSeq,
        @NotNull List<@Valid SyncMutationDto> mutations) {
}
