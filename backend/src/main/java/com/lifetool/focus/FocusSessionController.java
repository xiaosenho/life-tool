package com.lifetool.focus;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lifetool.common.ApiResponse;
import com.lifetool.focus.dto.CreateFocusSessionRequest;
import com.lifetool.focus.dto.EndFocusSessionRequest;
import com.lifetool.focus.dto.FocusSessionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/focus")
public class FocusSessionController {

    private final FocusSessionService service;

    public FocusSessionController(FocusSessionService service) {
        this.service = service;
    }

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<FocusSessionResponse>> startSession(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateFocusSessionRequest request) {
        FocusSessionResponse response = service.startSession(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PatchMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<FocusSessionResponse>> endSession(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @RequestBody EndFocusSessionRequest request) {
        FocusSessionResponse response = service.endSession(userId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<FocusSessionResponse>>> listSessions(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) String month) {
        List<FocusSessionResponse> response = service.listSessions(userId, month);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<FocusSessionResponse>> getSession(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        FocusSessionResponse response = service.getSession(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
