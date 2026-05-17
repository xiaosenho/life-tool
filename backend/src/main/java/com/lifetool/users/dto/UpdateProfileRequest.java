package com.lifetool.users.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 50) String displayName,
        String avatarAssetId
) {
}
