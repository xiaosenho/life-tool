package com.lifetool.media;

import com.lifetool.common.ApiResponse;
import com.lifetool.media.dto.AssetResponse;
import com.lifetool.media.dto.CreateAssetRequest;
import com.lifetool.media.dto.UploadTokenRequest;
import com.lifetool.media.dto.UploadTokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/upload-token")
    public ResponseEntity<ApiResponse<UploadTokenResponse>> uploadToken(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UploadTokenRequest req) {
        UploadTokenResponse resp = mediaService.generateUploadToken(userId, req);
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @PostMapping("/assets")
    public ResponseEntity<ApiResponse<AssetResponse>> createAsset(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateAssetRequest req) {
        AssetResponse resp = mediaService.createAsset(userId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(resp));
    }

    @GetMapping("/assets/{id}")
    public ResponseEntity<ApiResponse<AssetResponse>> getAsset(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        AssetResponse resp = mediaService.getAsset(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }

    @DeleteMapping("/assets/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        mediaService.deleteAsset(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
