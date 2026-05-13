package com.lifetool.focus;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lifetool.common.ApiResponse;
import com.lifetool.focus.dto.FocusPreferenceResponse;
import com.lifetool.focus.dto.UpdateFocusPreferenceRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/focus")
@Validated
public class FocusPreferenceController {

    private final FocusPreferenceService service;

    public FocusPreferenceController(FocusPreferenceService service) {
        this.service = service;
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<FocusPreferenceResponse>> getPreference(
            @AuthenticationPrincipal String userId) {
        FocusPreferenceResponse response = service.getPreference(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/preferences")
    public ResponseEntity<ApiResponse<FocusPreferenceResponse>> updatePreference(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UpdateFocusPreferenceRequest request) {
        FocusPreferenceResponse response = service.updatePreference(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
