package com.lifetool.friends;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lifetool.common.ApiResponse;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FriendService.FriendInfo>>> listFriends(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(friendService.listFriends(userId)));
    }

    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<FriendRequest>> sendRequest(
            @AuthenticationPrincipal String userId,
            @RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("VALIDATION_ERROR", "email is required"));
        }
        FriendRequest request = friendService.sendRequest(userId, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(request));
    }

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<List<FriendRequest>>> listRequests(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(friendService.listRequests(userId)));
    }

    @PatchMapping("/requests/{id}")
    public ResponseEntity<ApiResponse<FriendRequest>> handleRequest(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String action = body.get("action");
        if (action == null || action.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("VALIDATION_ERROR", "action is required"));
        }
        FriendRequest result = friendService.handleRequest(userId, id, action);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/{friendUserId}")
    public ResponseEntity<ApiResponse<Void>> removeFriend(
            @AuthenticationPrincipal String userId,
            @PathVariable String friendUserId) {
        friendService.removeFriend(userId, friendUserId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
