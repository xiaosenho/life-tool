package com.lifetool.devices;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lifetool.common.ApiResponse;
import com.lifetool.devices.dto.DeviceResponse;
import com.lifetool.devices.dto.RegisterDeviceRequest;
import com.lifetool.devices.dto.UpdateDeviceRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> list(
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(deviceService.listByUser(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeviceResponse>> register(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody RegisterDeviceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(deviceService.register(userId, request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<DeviceResponse>> update(
            @AuthenticationPrincipal String userId,
            @PathVariable String id,
            @RequestBody UpdateDeviceRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(deviceService.update(userId, id, request)));
    }
}
