package com.lifetool.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateAssetRequest(
        @NotBlank String assetId,
        @NotBlank String objectKey,
        @NotBlank String contentType,
        @NotBlank String purpose,
        @NotNull @Positive Long fileSize,
        Integer width,
        Integer height) {
}
