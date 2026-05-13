package com.lifetool.events;

import java.time.LocalDate;
import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lifetool.common.ApiResponse;
import com.lifetool.events.dto.CreateEventRequest;
import com.lifetool.events.dto.EventResponse;
import com.lifetool.events.dto.UpdateEventRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponse>>> listEvents(
            @AuthenticationPrincipal String userId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.listEvents(userId, from, to)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.createEvent(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @RequestBody UpdateEventRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.updateEvent(userId, id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        eventService.deleteEvent(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<EventResponse>>> upcomingEvents(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.ok(eventService.upcomingEvents(userId, days)));
    }
}
