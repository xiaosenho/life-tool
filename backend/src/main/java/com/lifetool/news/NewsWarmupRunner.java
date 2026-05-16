package com.lifetool.news;

import java.util.concurrent.CompletableFuture;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NewsWarmupRunner {

    private final NewsService newsService;

    public NewsWarmupRunner(NewsService newsService) {
        this.newsService = newsService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmupAfterStartup() {
        CompletableFuture.runAsync(newsService::warmUpCache);
    }
}
