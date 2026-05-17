package com.lifetool.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "lifetool.push.aliyun", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopPushNotificationService implements PushNotificationService {
    private static final Logger log = LoggerFactory.getLogger(NoopPushNotificationService.class);

    @Override
    public void pushToUser(PushNotificationCommand command) {
        log.debug("Skip push, provider disabled. targetUserId={}, title={}", command.targetUserId(), command.title());
    }
}
