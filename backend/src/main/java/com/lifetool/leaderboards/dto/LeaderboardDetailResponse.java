package com.lifetool.leaderboards.dto;

import java.util.List;

public record LeaderboardDetailResponse(
        String period,
        String metric,
        List<LeaderboardEntryDto> entries,
        LeaderboardEntryDto self,
        long gapToPrevious,
        int totalParticipants
) {
}
