package com.lifetool.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, UserDto user) {

    public record UserDto(String id, String email, String displayName, String avatarAssetId, String avatarUrl) {
    }
}
