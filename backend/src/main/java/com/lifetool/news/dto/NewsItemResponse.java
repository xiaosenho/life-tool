package com.lifetool.news.dto;

public record NewsItemResponse(
        String title,
        String source,
        String url,
        String publishedAt,
        String summary,
        String imageUrl
) {
}
