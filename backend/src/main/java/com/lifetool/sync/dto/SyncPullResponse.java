package com.lifetool.sync.dto;

import java.util.List;

public record SyncPullResponse(
        List<SyncChangeDto> changes,
        String nextCursor,
        Boolean hasMore) {
}
