package com.lifetool.news;

import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final List<FeedConfig> FEEDS = List.of(
            new FeedConfig("中新网", "https://www.chinanews.com.cn/rss/importnews.xml"),
            new FeedConfig("中新网", "https://www.chinanews.com.cn/rss/china.xml"),
            new FeedConfig("中新网", "https://www.chinanews.com.cn/rss/finance.xml"));
    private static final int MAX_ITEMS = 12;

    private final RestClient restClient;

    public NewsService() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Duration.ofSeconds(8).toMillis();
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public List<NewsItemResponse> getTopNews() {
        List<NewsItemResponse> results = new ArrayList<>();
        try {
            for (FeedConfig feed : FEEDS) {
                if (results.size() >= MAX_ITEMS) {
                    break;
                }
                log.info("Fetching top news feed from {}", feed.url());
                String xml = restClient.get()
                        .uri(feed.url())
                        .retrieve()
                        .body(String.class);
                mergeFeedItems(results, parseRss(xml, feed.source()));
            }
            log.info("Fetched domestic top news successfully, count={}", results.size());
            return results;
        } catch (Exception ex) {
            log.warn("Failed to fetch top news feed, returning empty list", ex);
            return List.of();
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

    private List<NewsItemResponse> parseRss(String xml, String defaultSource) throws Exception {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
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
                    extractImageUrl(rawDescription)));
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

    private String extractImageUrl(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<img[^>]+src=[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private record FeedConfig(String source, String url) {
    }
}
