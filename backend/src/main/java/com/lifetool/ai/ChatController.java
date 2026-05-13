package com.lifetool.ai;

import com.lifetool.ai.dto.ChatMessage;
import com.lifetool.ai.dto.ChatSession;
import com.lifetool.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<ChatSession>> createSession(
            @AuthenticationPrincipal String userId,
            @RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.get("title") : null;
        ChatSession session = chatService.createSession(userId, title);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(session));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<ChatSession>>> listSessions(
            @AuthenticationPrincipal String userId) {
        List<ChatSession> sessions = chatService.listSessions(userId);
        return ResponseEntity.ok(ApiResponse.ok(sessions));
    }

    @PostMapping("/sessions/{id}/messages")
    public ResponseEntity<ApiResponse<ChatMessage>> sendMessage(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("VALIDATION_ERROR", "content is required"));
        }
        ChatMessage msg = chatService.sendMessage(userId, id, content);
        return ResponseEntity.ok(ApiResponse.ok(msg));
    }

    @GetMapping("/sessions/{id}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> listMessages(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        List<ChatMessage> messages = chatService.listMessages(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(messages));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        chatService.deleteSession(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
