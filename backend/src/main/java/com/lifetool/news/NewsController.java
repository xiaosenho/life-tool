package com.lifetool.news;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lifetool.common.ApiResponse;
import com.lifetool.news.dto.NewsItemResponse;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping("/top")
    public ResponseEntity<ApiResponse<List<NewsItemResponse>>> topNews() {
        return ResponseEntity.ok(ApiResponse.ok(newsService.getTopNews()));
    }
}
