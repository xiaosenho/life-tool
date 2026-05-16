package com.lifetool.ai.dto;

import jakarta.validation.constraints.AssertTrue;

public record FoodRecognitionRequest(
        String imageUrl,
        String customPrompt,
        String mealType,
        String mediaAssetId
) {
    @AssertTrue(message = "mediaAssetId is required")
    public boolean hasImageSource() {
        return hasText(mediaAssetId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
