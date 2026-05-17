package com.lifetool.users;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.lifetool.auth.AuthException;
import com.lifetool.auth.dto.AuthResponse.UserDto;
import com.lifetool.media.MediaException;
import com.lifetool.media.MediaService;
import com.lifetool.users.dto.ChangePasswordRequest;
import com.lifetool.users.dto.UpdateProfileRequest;

@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MediaService mediaService;

    public UserProfileService(UserRepository userRepository, PasswordEncoder passwordEncoder, MediaService mediaService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mediaService = mediaService;
    }

    public UserDto getProfile(String userId) {
        return toDto(findUser(userId));
    }

    public UserDto updateProfile(String userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        if (request.displayName() != null) {
            String displayName = request.displayName().trim();
            if (displayName.isBlank()) {
                throw new AuthException("VALIDATION_ERROR", "displayName is required");
            }
            user.setDisplayName(displayName);
        }
        if (request.avatarAssetId() != null) {
            String avatarAssetId = request.avatarAssetId().trim();
            if (avatarAssetId.isBlank()) {
                user.setAvatarAssetId(null);
            } else {
                mediaService.findOwnedAsset(userId, avatarAssetId);
                mediaService.generateReadUrl(userId, avatarAssetId, "avatar");
                user.setAvatarAssetId(avatarAssetId);
            }
        }
        userRepository.save(user);
        return toDto(user);
    }

    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthException("UNAUTHORIZED", "当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new AuthException("VALIDATION_ERROR", "新密码不能和当前密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    public UserDto toDto(User user) {
        String avatarAssetId = user.getAvatarAssetId();
        String avatarUrl = null;
        if (avatarAssetId != null && !avatarAssetId.isBlank()) {
            try {
                avatarUrl = mediaService.generateReadUrl(user.getId(), avatarAssetId, "avatar");
            } catch (MediaException ex) {
                avatarAssetId = null;
            }
        }
        return new UserDto(user.getId(), user.getEmail(), user.getDisplayName(), avatarAssetId, avatarUrl);
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("NOT_FOUND", "User not found"));
    }
}
