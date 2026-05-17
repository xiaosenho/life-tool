package com.lifetool.friends.realtime;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/friends/events")
public class FriendRealtimeController {

    private final FriendRealtimeBroker broker;

    public FriendRealtimeController(FriendRealtimeBroker broker) {
        this.broker = broker;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal String userId) {
        return broker.subscribe(userId);
    }
}
