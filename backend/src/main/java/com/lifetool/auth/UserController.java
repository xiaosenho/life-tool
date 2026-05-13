package com.lifetool.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lifetool.auth.dto.AuthResponse.UserDto;
import com.lifetool.common.ApiResponse;
import com.lifetool.users.User;
import com.lifetool.users.UserRepository;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> me(@AuthenticationPrincipal String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("NOT_FOUND", "User not found"));
        return ResponseEntity.ok(ApiResponse.ok(
                new UserDto(user.getId(), user.getEmail(), user.getDisplayName())));
    }
}
