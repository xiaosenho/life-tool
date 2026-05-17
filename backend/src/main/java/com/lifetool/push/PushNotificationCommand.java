package com.lifetool.push;

import java.util.Map;

public record PushNotificationCommand(
        String title,
        String body,
        String targetUserId,
        String deepLink,
        Map<String, Object> extras
) {
}
