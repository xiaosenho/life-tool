package com.lifetool.ai.dto;

import java.util.List;

public record LifeAdviceResponse(
        String summary,
        List<String> suggestions,
        String disclaimer
) {}
