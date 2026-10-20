package com.example.newsservice.service;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class RssFetcher {

    private final RestClient rssClient;

    @Retry(name = "rssFetch", fallbackMethod = "fetchFallback")
    public byte[] fetch(String url) {
        return rssClient.get().uri(url).retrieve().body(byte[].class);
    }

    private byte[] fetchFallback(String url, Throwable t) {
        log.warn("RSS fetch failed for {}: {}", url, t.toString());
        return null;
    }
}