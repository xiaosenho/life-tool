package com.lifetool.ai.dto;

import java.util.List;

public record LifeAdviceRequest(
        String period,
        List<String> topics
) {
}
