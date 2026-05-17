package com.lifetool.push;

public interface PushNotificationService {
    void pushToUser(PushNotificationCommand command);
}
