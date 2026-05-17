package com.lifetool.friends;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.lifetool.friends.realtime.FriendRealtimeService;
import com.lifetool.friends.dto.FriendConversationSummaryResponse;
import com.lifetool.friends.dto.FriendMessageAttachmentRequest;
import com.lifetool.friends.dto.FriendMessagePageResponse;
import com.lifetool.friends.dto.FriendMessageResponse;
import com.lifetool.media.MediaAsset;
import com.lifetool.media.MediaException;
import com.lifetool.media.MediaService;
import com.lifetool.users.User;
import com.lifetool.users.UserRepository;

@Service
public class FriendService {
    private static final int DEFAULT_MESSAGE_PAGE_LIMIT = 50;
    private static final int MAX_MESSAGE_PAGE_LIMIT = 100;

    private final FriendStore store;
    private final FriendMessageStore messageStore;
    private final UserRepository userRepo;
    private final MediaService mediaService;
    private final FriendRealtimeService realtimeService;

    public FriendService(
            FriendStore store,
            FriendMessageStore messageStore,
            UserRepository userRepo,
            MediaService mediaService,
            FriendRealtimeService realtimeService
    ) {
        this.store = store;
        this.messageStore = messageStore;
        this.userRepo = userRepo;
        this.mediaService = mediaService;
        this.realtimeService = realtimeService;
    }

    public FriendRequest sendRequest(String fromUserId, String targetEmail) {
        User target = userRepo.findByEmail(targetEmail)
                .orElseThrow(() -> new FriendException("NOT_FOUND", "User not found"));

        if (target.getId().equals(fromUserId)) {
            throw new FriendException("VALIDATION_ERROR", "Cannot add yourself as a friend");
        }
        if (store.areFriends(fromUserId, target.getId())) {
            throw new FriendException("CONFLICT", "Already friends");
        }
        if (store.findPendingRequestBetween(fromUserId, target.getId()).isPresent()) {
            throw new FriendException("CONFLICT", "Friend request already pending");
        }

        FriendRequest request = store.saveRequest(new FriendRequest(fromUserId, target.getId()));
        realtimeService.publishRequestCreated(target.getId(), request);
        return request;
    }

    public List<FriendRequest> listRequests(String userId) {
        return store.findRequestsByUser(userId);
    }

    public FriendRequest handleRequest(String userId, String requestId, String action) {
        FriendRequest request = store.findRequestById(requestId)
                .orElseThrow(() -> new FriendException("NOT_FOUND", "Request not found"));

        if (!request.getToUserId().equals(userId)) {
            throw new FriendException("FORBIDDEN", "Only the recipient can respond to this request");
        }
        if (request.getStatus() != FriendRequest.Status.PENDING) {
            throw new FriendException("CONFLICT", "Request already handled");
        }

        if ("accept".equals(action)) {
            request.setStatus(FriendRequest.Status.ACCEPTED);
            store.saveFriendship(new Friendship(request.getFromUserId(), request.getToUserId()));
        } else if ("reject".equals(action)) {
            request.setStatus(FriendRequest.Status.REJECTED);
            store.saveRequest(request);
        } else {
            throw new FriendException("VALIDATION_ERROR", "Invalid action, must be 'accept' or 'reject'");
        }

        realtimeService.publishRequestUpdated(request.getFromUserId(), request);
        realtimeService.publishRequestUpdated(request.getToUserId(), request);
        return request;
    }

    public record FriendInfo(String userId, String email, String displayName, String avatarUrl) {}

    public List<FriendInfo> listFriends(String userId) {
        List<String> friendIds = store.findFriendships(userId).stream()
                .map(friendship -> friendship.getUserId().equals(userId)
                        ? friendship.getFriendUserId()
                        : friendship.getUserId())
                .distinct()
                .toList();
        Map<String, User> friendsById = userRepo.findByIds(friendIds);
        return friendIds.stream()
                .map(friendsById::get)
                .filter(friend -> friend != null)
                .map(friend -> new FriendInfo(friend.getId(), friend.getEmail(), friend.getDisplayName(), resolveAvatarUrl(friend)))
                .toList();
    }

    public void removeFriend(String userId, String friendUserId) {
        if (!store.areFriends(userId, friendUserId)) {
            throw new FriendException("NOT_FOUND", "Not friends");
        }
        store.removeFriendship(userId, friendUserId);
    }

    public FriendMessage sendMessage(String userId, String friendUserId, String content, String type) {
        return sendMessage(userId, friendUserId, content, type, null);
    }

    public FriendMessage sendMessage(String userId, String friendUserId, String content, String type, FriendMessageAttachmentRequest attachmentRequest) {
        ensureFriends(userId, friendUserId);
        FriendMessage.MessageType messageType = parseMessageType(type);
        FriendMessageAttachment attachment = buildAttachment(userId, messageType, attachmentRequest);
        String normalized = normalizeMessageContent(content, messageType, attachment);
        FriendMessage saved = messageStore.save(new FriendMessage(userId, friendUserId, messageType, normalized, attachment));
        FriendMessageResponse response = toMessageResponse(friendUserId, saved);
        FriendConversationSummaryResponse conversation = getConversationSummary(friendUserId, userId);
        String senderDisplayName = userRepo.findById(userId).map(User::getDisplayName).orElse("新消息");
        realtimeService.publishMessageCreated(friendUserId, response, conversation, senderDisplayName);
        return saved;
    }

    public FriendMessagePageResponse listConversation(String userId, String friendUserId, Integer limit, java.time.Instant beforeCreatedAt, String beforeId) {
        ensureFriends(userId, friendUserId);
        int normalizedLimit = normalizeMessagePageLimit(limit);
        FriendMessageStore.ConversationPage page = messageStore.listConversation(userId, friendUserId, normalizedLimit, beforeCreatedAt, beforeId);
        return new FriendMessagePageResponse(
                page.messages().stream()
                        .map(message -> toMessageResponse(userId, message))
                        .toList(),
                normalizedLimit,
                page.hasMore());
    }

    public FriendMessageResponse toMessageResponse(String viewerUserId, FriendMessage message) {
        return FriendMessageResponse.from(message, refreshAttachment(message.getFromUserId(), message.getAttachment()));
    }

    public int markConversationRead(String userId, String friendUserId) {
        ensureFriends(userId, friendUserId);
        int updated = messageStore.markConversationRead(userId, friendUserId);
        FriendConversationSummaryResponse summary = getConversationSummary(userId, friendUserId);
        realtimeService.publishConversationRead(friendUserId, userId, updated, getConversationSummary(friendUserId, userId));
        realtimeService.publishConversationRead(userId, friendUserId, updated, summary);
        return updated;
    }

    public List<FriendConversationSummaryResponse> listConversations(String userId) {
        List<String> friendIds = store.findFriendships(userId).stream()
                .map(friendship -> friendship.getUserId().equals(userId)
                        ? friendship.getFriendUserId()
                        : friendship.getUserId())
                .distinct()
                .toList();
        Map<String, FriendInfo> friends = userRepo.findByIds(friendIds).values().stream()
                .collect(Collectors.toMap(User::getId, user -> new FriendInfo(user.getId(), user.getEmail(), user.getDisplayName(), resolveAvatarUrl(user))));
        return messageStore.listConversationSummaries(userId).stream()
                .map(summary -> {
                    FriendInfo info = friends.get(summary.friendUserId());
                    if (info == null) {
                        return null;
                    }
                    return new FriendConversationSummaryResponse(
                            summary.friendUserId(),
                            info.displayName(),
                            info.email(),
                            info.avatarUrl(),
                            summary.lastMessage(),
                            summary.lastMessageType(),
                            summary.lastMessageAt(),
                            summary.unreadCount());
                })
                .filter(item -> item != null)
                .toList();
    }

    public FriendConversationSummaryResponse getConversationSummary(String userId, String friendUserId) {
        if (!store.areFriends(userId, friendUserId)) {
            return null;
        }
        var summary = messageStore.getConversationSummary(userId, friendUserId);
        if (summary == null) {
            return null;
        }
        User friend = userRepo.findById(friendUserId).orElse(null);
        if (friend == null) {
            return null;
        }
        return new FriendConversationSummaryResponse(
                summary.friendUserId(),
                friend.getDisplayName(),
                friend.getEmail(),
                resolveAvatarUrl(friend),
                summary.lastMessage(),
                summary.lastMessageType(),
                summary.lastMessageAt(),
                summary.unreadCount()
        );
    }

    private void ensureFriends(String userId, String friendUserId) {
        if (!store.areFriends(userId, friendUserId)) {
            throw new FriendException("FORBIDDEN", "Only friends can interact");
        }
    }

    private int normalizeMessagePageLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_MESSAGE_PAGE_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_MESSAGE_PAGE_LIMIT));
    }

    private FriendMessage.MessageType parseMessageType(String type) {
        if (type == null || type.isBlank()) {
            return FriendMessage.MessageType.TEXT;
        }
        return switch (type.trim().toLowerCase()) {
            case "cheer" -> FriendMessage.MessageType.CHEER;
            case "celebrate" -> FriendMessage.MessageType.CELEBRATE;
            case "hug" -> FriendMessage.MessageType.HUG;
            case "coffee" -> FriendMessage.MessageType.COFFEE;
            case "poke" -> FriendMessage.MessageType.POKE;
            case "image" -> FriendMessage.MessageType.IMAGE;
            case "audio" -> FriendMessage.MessageType.AUDIO;
            default -> FriendMessage.MessageType.TEXT;
        };
    }

    private String normalizeMessageContent(String content, FriendMessage.MessageType messageType, FriendMessageAttachment attachment) {
        String normalized = content == null ? "" : content.trim();
        if (messageType == FriendMessage.MessageType.IMAGE) {
            return normalized.isBlank() ? "[图片]" : normalized;
        }
        if (messageType == FriendMessage.MessageType.AUDIO) {
            return normalized.isBlank() ? "[语音]" : normalized;
        }
        if (attachment != null && normalized.isBlank()) {
            return attachment.kind().equals("audio") ? "[语音]" : "[图片]";
        }
        if (normalized.isEmpty()) {
            throw new FriendException("VALIDATION_ERROR", "content is required");
        }
        return normalized;
    }

    private FriendMessageAttachment buildAttachment(
            String userId,
            FriendMessage.MessageType messageType,
            FriendMessageAttachmentRequest attachmentRequest) {
        if (attachmentRequest == null || attachmentRequest.assetId() == null || attachmentRequest.assetId().isBlank()) {
            return null;
        }
        MediaAsset asset = mediaService.findOwnedAsset(userId, attachmentRequest.assetId());
        boolean audio = asset.getContentType().startsWith("audio/");
        if (messageType == FriendMessage.MessageType.IMAGE && audio) {
            throw new FriendException("VALIDATION_ERROR", "image message requires image asset");
        }
        if (messageType == FriendMessage.MessageType.AUDIO && !audio) {
            throw new FriendException("VALIDATION_ERROR", "audio message requires audio asset");
        }
        return new FriendMessageAttachment(
                asset.getId(),
                audio ? "audio" : "image",
                asset.getContentType(),
                null,
                attachmentRequest.width(),
                attachmentRequest.height(),
                attachmentRequest.durationSeconds());
    }

    private FriendMessageAttachment refreshAttachment(String userId, FriendMessageAttachment attachment) {
        if (attachment == null || attachment.assetId() == null || attachment.assetId().isBlank()) {
            return attachment;
        }
        try {
            String purpose = "audio".equals(attachment.kind()) ? "chat_audio" : "chat_image";
            return new FriendMessageAttachment(
                    attachment.assetId(),
                    attachment.kind(),
                    attachment.contentType(),
                    mediaService.generateReadUrl(userId, attachment.assetId(), purpose),
                    attachment.width(),
                    attachment.height(),
                    attachment.durationSeconds());
        } catch (RuntimeException ex) {
            return attachment;
        }
    }

    private String resolveAvatarUrl(User user) {
        if (user == null || user.getAvatarAssetId() == null || user.getAvatarAssetId().isBlank()) {
            return null;
        }
        try {
            return mediaService.generateReadUrl(user.getId(), user.getAvatarAssetId(), "avatar");
        } catch (MediaException ex) {
            return null;
        }
    }

}
