package com.lifetool.friends.realtime;

import org.springframework.stereotype.Service;

import com.lifetool.friends.FriendRequest;
import com.lifetool.friends.dto.FriendConversationSummaryResponse;
import com.lifetool.friends.dto.FriendMessageResponse;
import com.lifetool.push.PushNotificationCommand;
import com.lifetool.push.PushNotificationService;

@Service
public class FriendRealtimeService {

    private final FriendRealtimeBroker broker;
    private final PushNotificationService pushNotificationService;

    public FriendRealtimeService(FriendRealtimeBroker broker, PushNotificationService pushNotificationService) {
        this.broker = broker;
        this.pushNotificationService = pushNotificationService;
    }

    public void publishMessageCreated(
            String recipientUserId,
            FriendMessageResponse message,
            FriendConversationSummaryResponse conversation,
            String senderDisplayName
    ) {
        broker.publish(recipientUserId, FriendEventType.FRIEND_MESSAGE_CREATED, new FriendMessageEventPayload(message, conversation));
        if (!broker.hasActiveSubscriber(recipientUserId)) {
            pushNotificationService.pushToUser(new PushNotificationCommand(
                    senderDisplayName,
                    summarizeMessage(message),
                    recipientUserId,
                    "lifetool://friend-chat?friendUserId=" + message.fromUserId(),
                    java.util.Map.of(
                            "scene", "friend_message",
                            "friendUserId", message.fromUserId()
                    )
            ));
        }
    }

    public void publishRequestCreated(String targetUserId, FriendRequest request) {
        broker.publish(targetUserId, FriendEventType.FRIEND_REQUEST_CREATED, new FriendRequestEventPayload(request));
        if (!broker.hasActiveSubscriber(targetUserId)) {
            pushNotificationService.pushToUser(new PushNotificationCommand(
                    "新的好友申请",
                    "有人想添加你为好友",
                    targetUserId,
                    "lifetool://friends?tab=friends",
                    java.util.Map.of("scene", "friend_request")
            ));
        }
    }

    public void publishRequestUpdated(String targetUserId, FriendRequest request) {
        broker.publish(targetUserId, FriendEventType.FRIEND_REQUEST_UPDATED, new FriendRequestEventPayload(request));
    }

    public void publishConversationRead(String userId, String friendUserId, int updated, FriendConversationSummaryResponse summary) {
        broker.publish(userId, FriendEventType.FRIEND_CONVERSATION_READ, new FriendReadEventPayload(friendUserId, updated, summary));
    }

    private String summarizeMessage(FriendMessageResponse message) {
        if ("image".equals(message.type())) {
            return "[图片]";
        }
        if ("audio".equals(message.type())) {
            return "[语音]";
        }
        return message.content();
    }
}
