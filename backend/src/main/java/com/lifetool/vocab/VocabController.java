package com.lifetool.vocab;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lifetool.common.ApiResponse;
import com.lifetool.vocab.dto.UpdateVocabProgressRequest;
import com.lifetool.vocab.dto.VocabBookResponse;
import com.lifetool.vocab.dto.VocabPageResponse;
import com.lifetool.vocab.dto.VocabProgressResponse;

@RestController
@RequestMapping("/api/vocab")
public class VocabController {

    private final VocabService service;

    public VocabController(VocabService service) {
        this.service = service;
    }

    @GetMapping("/books")
    public ResponseEntity<ApiResponse<List<VocabBookResponse>>> listBooks() {
        return ResponseEntity.ok(ApiResponse.ok(service.listBooks()));
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<VocabPageResponse>> getPage(
            @RequestParam String bookCode,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(service.getPage(bookCode, offset, limit)));
    }

    @GetMapping("/progress")
    public ResponseEntity<ApiResponse<VocabProgressResponse>> getProgress(
            @AuthenticationPrincipal String userId,
            @RequestParam String bookCode) {
        return ResponseEntity.ok(ApiResponse.ok(service.getProgress(userId, bookCode)));
    }

    @PutMapping("/progress")
    public ResponseEntity<ApiResponse<VocabProgressResponse>> updateProgress(
            @AuthenticationPrincipal String userId,
            @RequestBody UpdateVocabProgressRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateProgress(userId, request)));
    }
}
