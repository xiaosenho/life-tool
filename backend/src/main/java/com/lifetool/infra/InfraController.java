package com.lifetool.infra;

import com.lifetool.common.ApiResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/infra")
public class InfraController {

    private final InfraHealthService infraHealthService;

    public InfraController(InfraHealthService infraHealthService) {
        this.infraHealthService = infraHealthService;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.ok(infraHealthService.check()));
    }
}
