package com.lifetool.sync.dto;

import java.util.List;

public record SyncPushResponse(
        List<SyncMutationResultDto> applied,
        List<SyncMutationResultDto> rejected,
        List<SyncConflictDto> conflicts,
        String serverCursor) {
}
