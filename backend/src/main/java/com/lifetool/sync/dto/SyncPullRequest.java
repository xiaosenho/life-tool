package com.lifetool.sync.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record SyncPullRequest(
        @NotBlank String deviceId,
        String cursor,
        List<String> entityTypes) {
}
