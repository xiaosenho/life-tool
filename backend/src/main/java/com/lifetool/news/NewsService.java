package com.lifetool.news;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.lifetool.news.dto.NewsItemResponse;

@Service
public class NewsService {
    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private static final String NEWS_CACHE_KEY = "news:top:v1";
    private static final List<FeedConfig> FEEDS = List.of(
            new FeedConfig("联合早报", "https://plink.anyfeeder.com/zaobao/realtime/china"));
    private static final int MAX_ITEMS = 12;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final AtomicReference<CachedNews> cacheRef = new AtomicReference<>(CachedNews.empty());

    public NewsService(
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Duration.ofSeconds(8).toMillis();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public List<NewsItemResponse> getTopNews() {
        List<NewsItemResponse> redisCached = readRedisCache();
        if (!redisCached.isEmpty()) {
            log.debug("Returning Redis cached top news, count={}", redisCached.size());
            cacheRef.set(new CachedNews(redisCached, Instant.now()));
            return redisCached;
        }

        CachedNews cached = cacheRef.get();
        if (cached.isFresh()) {
            log.debug("Returning cached top news, count={}", cached.items().size());
            return cached.items();
        }

        List<NewsItemResponse> results = new ArrayList<>();
        try {
            for (FeedConfig feed : FEEDS) {
                if (results.size() >= MAX_ITEMS) {
                    break;
                }
                log.info("Fetching top news feed from {}", feed.url());
                byte[] xmlBytes = restClient.get()
                        .uri(feed.url())
                        .retrieve()
                        .body(byte[].class);
                mergeFeedItems(results, parseRss(xmlBytes, feed.source()));
            }
            cacheRef.set(new CachedNews(List.copyOf(results), Instant.now()));
            writeRedisCache(results);
            log.info("Fetched domestic top news successfully, count={}", results.size());
            return results;
        } catch (Exception ex) {
            if (!cached.items().isEmpty()) {
                log.warn("Failed to refresh top news feed, returning stale cache. count={}", cached.items().size(), ex);
                return cached.items();
            }
            log.warn("Failed to fetch top news feed, returning empty list", ex);
            return List.of();
        }
    }

    public void warmUpCache() {
        try {
            log.info("Starting async news cache warmup");
            List<NewsItemResponse> items = getTopNews();
            log.info("Finished async news cache warmup, count={}", items.size());
        } catch (Exception ex) {
            log.warn("News cache warmup failed", ex);
        }
    }

    private void mergeFeedItems(List<NewsItemResponse> results, List<NewsItemResponse> candidates) {
        for (NewsItemResponse item : candidates) {
            boolean exists = results.stream().anyMatch(existing -> existing.url().equals(item.url()));
            if (!exists) {
                results.add(item);
            }
            if (results.size() >= MAX_ITEMS) {
                return;
            }
        }
    }

    private List<NewsItemResponse> parseRss(byte[] xmlBytes, String defaultSource) throws Exception {
        if (xmlBytes == null || xmlBytes.length == 0) {
            return List.of();
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(xmlBytes)));
        NodeList items = document.getElementsByTagName("item");
        List<NewsItemResponse> results = new ArrayList<>();
        for (int index = 0; index < Math.min(items.getLength(), MAX_ITEMS); index++) {
            Element item = (Element) items.item(index);
            String title = textOf(item, "title");
            String link = textOf(item, "link");
            String pubDate = textOf(item, "pubDate");
            String rawDescription = textOf(item, "description");
            String description = stripHtml(rawDescription);
            results.add(new NewsItemResponse(
                    title,
                    extractSource(title, defaultSource),
                    link,
                    pubDate,
                    description,
                    extractImageUrl(item, rawDescription)));
        }
        return results;
    }

    private String textOf(Element element, String tagName) {
        NodeList list = element.getElementsByTagName(tagName);
        if (list.getLength() == 0 || list.item(0) == null) {
            return "";
        }
        return list.item(0).getTextContent();
    }

    private String extractSource(String title, String defaultSource) {
        if (title == null || title.isBlank()) {
            return defaultSource;
        }
        int separatorIndex = title.lastIndexOf(" - ");
        if (separatorIndex < 0 || separatorIndex >= title.length() - 3) {
            return defaultSource;
        }
        return title.substring(separatorIndex + 3).trim();
    }

    private String stripHtml(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return input.replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String extractImageUrl(Element item, String rawDescription) {
        String mediaContent = attributeOf(item, "media:content", "url");
        if (hasText(mediaContent)) {
            return mediaContent;
        }

        String mediaThumbnail = attributeOf(item, "media:thumbnail", "url");
        if (hasText(mediaThumbnail)) {
            return mediaThumbnail;
        }

        String enclosure = attributeOf(item, "enclosure", "url");
        if (hasText(enclosure)) {
            return enclosure;
        }

        if (rawDescription == null || rawDescription.isBlank()) {
            return null;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<img[^>]+src=[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(rawDescription);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String attributeOf(Element element, String tagName, String attrName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || !(nodes.item(0) instanceof Element child)) {
            return null;
        }
        String value = child.getAttribute(attrName);
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<NewsItemResponse> readRedisCache() {
        if (redisTemplate == null) {
            return List.of();
        }
        try {
            String json = redisTemplate.opsForValue().get(NEWS_CACHE_KEY);
            if (!hasText(json)) {
                return List.of();
            }
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, NewsItemResponse.class));
        } catch (Exception ex) {
            log.warn("Failed to read news cache from Redis", ex);
            return List.of();
        }
    }

    private void writeRedisCache(List<NewsItemResponse> items) {
        if (redisTemplate == null || items.isEmpty()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(NEWS_CACHE_KEY, objectMapper.writeValueAsString(items), CACHE_TTL);
        } catch (Exception ex) {
            log.warn("Failed to write news cache to Redis", ex);
        }
    }

    private record FeedConfig(String source, String url) {
    }

    private record CachedNews(List<NewsItemResponse> items, Instant fetchedAt) {
        private static CachedNews empty() {
            return new CachedNews(List.of(), Instant.EPOCH);
        }

        private boolean isFresh() {
            return !items.isEmpty() && fetchedAt.plus(CACHE_TTL).isAfter(Instant.now());
        }
    }
}
