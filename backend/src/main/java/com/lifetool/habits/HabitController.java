package com.lifetool.habits;

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
import com.lifetool.habits.dto.CreateCheckinRequest;
import com.lifetool.habits.dto.CreateHabitRequest;
import com.lifetool.habits.dto.HabitCheckinResponse;
import com.lifetool.habits.dto.HabitResponse;
import com.lifetool.habits.dto.UpdateHabitRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/habits")
public class HabitController {

    private final HabitService service;

    public HabitController(HabitService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HabitResponse>> createHabit(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateHabitRequest request) {
        HabitResponse response = service.createHabit(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<HabitResponse>>> listHabits(
            @AuthenticationPrincipal String userId) {
        List<HabitResponse> response = service.listHabits(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<HabitResponse>> updateHabit(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @RequestBody UpdateHabitRequest request) {
        HabitResponse response = service.updateHabit(userId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> archiveHabit(
            @AuthenticationPrincipal String userId,
            @PathVariable String id) {
        service.archiveHabit(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/checkins")
    public ResponseEntity<ApiResponse<HabitCheckinResponse>> checkin(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @RequestBody CreateCheckinRequest request) {
        HabitCheckinResponse response = service.checkin(userId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/{id}/checkins")
    public ResponseEntity<ApiResponse<List<HabitCheckinResponse>>> listCheckins(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        List<HabitCheckinResponse> response = service.listCheckins(userId, id, from, to);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
