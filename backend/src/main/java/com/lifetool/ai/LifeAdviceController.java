package com.lifetool.ai;

import com.lifetool.ai.dto.LifeAdviceRequest;
import com.lifetool.ai.dto.LifeAdviceResponse;
import com.lifetool.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class LifeAdviceController {

    private final LifeAdviceService lifeAdviceService;

    public LifeAdviceController(LifeAdviceService lifeAdviceService) {
        this.lifeAdviceService = lifeAdviceService;
    }

    @PostMapping("/life-advice")
    public ResponseEntity<ApiResponse<LifeAdviceResponse>> getLifeAdvice(
            @AuthenticationPrincipal String userId,
            @RequestBody LifeAdviceRequest req) {
        LifeAdviceResponse resp = lifeAdviceService.getAdvice(userId, req);
        return ResponseEntity.ok(ApiResponse.ok(resp));
    }
}
