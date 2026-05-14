package com.lifetool.ai;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lifetool.ai.dto.AiChatMessageResponse;
import com.lifetool.ai.dto.AiChatMessagesResponse;
import com.lifetool.ai.dto.AiChatSessionResponse;
import com.lifetool.ai.dto.AiMemoriesResponse;
import com.lifetool.ai.dto.CreateAiChatSessionRequest;
import com.lifetool.ai.dto.LifeAdviceRequest;
import com.lifetool.ai.dto.LifeAdviceResponse;
import com.lifetool.ai.dto.SendAiMessageRequest;
import com.lifetool.common.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/life-advice")
    public ResponseEntity<ApiResponse<LifeAdviceResponse>> lifeAdvice(
            @AuthenticationPrincipal String userId,
            @RequestBody(required = false) LifeAdviceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(aiService.getLifeAdvice(userId, request)));
    }

    @PostMapping("/chat/sessions")
    public ResponseEntity<ApiResponse<AiChatSessionResponse>> createSession(
            @AuthenticationPrincipal String userId,
            @RequestBody(required = false) CreateAiChatSessionRequest request) {
        CreateAiChatSessionRequest safeRequest = request == null
                ? new CreateAiChatSessionRequest(null, true)
                : request;
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(aiService.createSession(userId, safeRequest)));
    }

    @PostMapping("/chat/sessions/{id}/messages")
    public ResponseEntity<ApiResponse<AiChatMessageResponse>> sendMessage(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @Valid @RequestBody SendAiMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(aiService.sendMessage(userId, id, request)));
    }

    @GetMapping("/chat/sessions/{id}/messages")
    public ResponseEntity<ApiResponse<AiChatMessagesResponse>> listMessages(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.ok(aiService.listMessages(userId, id)));
    }

    @GetMapping("/memories")
    public ResponseEntity<ApiResponse<AiMemoriesResponse>> listMemories(@AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(aiService.listMemories(userId)));
    }

    @DeleteMapping("/memories/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMemory(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        aiService.deleteMemory(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
