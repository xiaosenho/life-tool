package com.lifetool.leaderboards.dto;

public record LeaderboardEntryDto(
        String userId,
        String displayName,
        String avatarUrl,
        long value,
        int rank
) {
}
