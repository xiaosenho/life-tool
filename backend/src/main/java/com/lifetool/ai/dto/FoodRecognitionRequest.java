package com.lifetool.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record FoodRecognitionRequest(
        @NotBlank String imageUrl,
        String customPrompt
) {
}
