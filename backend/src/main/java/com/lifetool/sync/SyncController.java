package com.lifetool.sync;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lifetool.common.ApiResponse;
import com.lifetool.sync.dto.SyncPullRequest;
import com.lifetool.sync.dto.SyncPullResponse;
import com.lifetool.sync.dto.SyncPushRequest;
import com.lifetool.sync.dto.SyncPushResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/push")
    public ResponseEntity<ApiResponse<SyncPushResponse>> push(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody SyncPushRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(syncService.push(userId, request)));
    }

    @PostMapping("/pull")
    public ResponseEntity<ApiResponse<SyncPullResponse>> pull(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody SyncPullRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(syncService.pull(userId, request)));
    }
}
