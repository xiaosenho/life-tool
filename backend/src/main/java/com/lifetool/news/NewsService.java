package com.lifetool.news;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.lifetool.news.dto.NewsItemResponse;

@Service
public class NewsService {
    private static final String GOOGLE_NEWS_TOP_CN =
            "https://news.google.com/rss?hl=zh-CN&gl=CN&ceid=CN:zh-Hans";

    private final RestClient restClient = RestClient.builder().build();

    public List<NewsItemResponse> getTopNews() {
        try {
            String xml = restClient.get()
                    .uri(GOOGLE_NEWS_TOP_CN)
                    .retrieve()
                    .body(String.class);
            return parseRss(xml);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to fetch top news", ex);
        }
    }

    private List<NewsItemResponse> parseRss(String xml) throws Exception {
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
        for (int index = 0; index < Math.min(items.getLength(), 12); index++) {
            Element item = (Element) items.item(index);
            String title = textOf(item, "title");
            String link = textOf(item, "link");
            String pubDate = textOf(item, "pubDate");
            String rawDescription = textOf(item, "description");
            String description = stripHtml(rawDescription);
            results.add(new NewsItemResponse(
                    title,
                    extractSource(title),
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

    private String extractSource(String title) {
        if (title == null || title.isBlank()) {
            return "新闻";
        }
        int separatorIndex = title.lastIndexOf(" - ");
        if (separatorIndex < 0 || separatorIndex >= title.length() - 3) {
            return "新闻";
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
}
