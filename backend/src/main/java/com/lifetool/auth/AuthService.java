package com.lifetool.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.lifetool.auth.dto.AuthResponse;
import com.lifetool.auth.dto.LoginRequest;
import com.lifetool.auth.dto.RefreshRequest;
import com.lifetool.auth.dto.RegisterRequest;
import com.lifetool.users.User;
import com.lifetool.users.UserProfileService;
import com.lifetool.users.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final UserProfileService userProfileService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider,
                       UserProfileService userProfileService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.userProfileService = userProfileService;
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new AuthException("CONFLICT", "Email already registered");
        }
        User user = new User(req.email(), passwordEncoder.encode(req.password()), req.displayName());
        userRepository.save(user);
        return buildResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new AuthException("UNAUTHORIZED", "Invalid credentials"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new AuthException("UNAUTHORIZED", "Invalid credentials");
        }
        return buildResponse(user);
    }

    public AuthResponse refresh(RefreshRequest req) {
        String token = req.refreshToken();
        if (!jwtProvider.validate(token) || !"refresh".equals(jwtProvider.getTokenType(token))) {
            throw new AuthException("UNAUTHORIZED", "Invalid refresh token");
        }
        String userId = jwtProvider.getUserId(token);
        jwtProvider.revoke(token);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("UNAUTHORIZED", "User not found"));
        return buildResponse(user);
    }

    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null) jwtProvider.revoke(accessToken);
        if (refreshToken != null) jwtProvider.revoke(refreshToken);
    }

    private AuthResponse buildResponse(User user) {
        return new AuthResponse(
                jwtProvider.generateAccessToken(user.getId()),
                jwtProvider.generateRefreshToken(user.getId()),
                userProfileService.toDto(user));
    }
}
