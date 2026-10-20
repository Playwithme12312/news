package com.example.newsservice.service;

import com.example.newsservice.config.AppProperties;
import com.example.newsservice.domain.News;
import com.example.newsservice.repository.NewsRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RssCollector {

    private final NewsRepository repository;
    private final UrlNormalizer normalizer;
    private final RssFetcher fetcher;
    private final AppProperties props;

    @Scheduled(
            initialDelayString = "${news.collect-initial-delay:PT10S}",
            fixedDelayString = "${news.collect-interval:PT15M}"
    )
    public void scheduledCollect() {
        log.info("Scheduled collect: {}", collectAll());
    }

    public Map<String, Integer> collectAll() {
        Map<String, Integer> result = new LinkedHashMap<>();


        if (props.sources() == null || props.sources().isEmpty()) {
            log.warn("Список источников RSS пуст или не задан в application.yml!");
            return result;
        }

        for (String source : props.sources()) {
            result.put(source, collectSource(source));
        }
        return result;
    }

    public int collectSource(String url) {
        byte[] body = fetcher.fetch(url);
        if (body == null) return 0;
        try {
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(new ByteArrayInputStream(body)));
            int saved = 0;
            for (SyndEntry entry : feed.getEntries()) {
                if (saveEntry(url, entry)) saved++;
            }
            return saved;
        } catch (Exception e) {
            log.warn("Parse failed for {}: {}", url, e.toString());
            return 0;
        }
    }

    private boolean saveEntry(String source, SyndEntry entry) {
        String title = entry.getTitle() == null ? "" : entry.getTitle().trim();
        if (title.isEmpty()) return false;

        String link = entry.getLink() == null ? "" : entry.getLink().trim();
        String key = link.isEmpty()
                ? title + "|" + (entry.getPublishedDate() == null ? "" : entry.getPublishedDate())
                : normalizer.normalize(link);
        String hash = normalizer.sha256(key);

        if (repository.existsByUrlHash(hash)) return false;

        Instant published = entry.getUpdatedDate() != null
                ? entry.getUpdatedDate().toInstant()
                : entry.getPublishedDate() != null
                ? entry.getPublishedDate().toInstant()
                : Instant.now();

        News news = News.builder()
                .source(source)
                .title(title.length() > 1024 ? title.substring(0, 1024) : title)
                .link(link.length() > 2048 ? link.substring(0, 2048) : link)
                .summary(entry.getDescription() != null ? entry.getDescription().getValue() : null)
                .publishedAt(published)
                .fetchedAt(Instant.now())
                .urlHash(hash)
                .build();
        try {
            repository.save(news);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}