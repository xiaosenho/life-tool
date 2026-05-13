package com.lifetool.leaderboards.dto;

import java.util.List;

public record LeaderboardResponse(
        String period,
        String metric,
        List<LeaderboardEntryDto> entries
) {
}
