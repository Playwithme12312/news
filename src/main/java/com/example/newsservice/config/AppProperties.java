package com.example.newsservice.config;
//настройка
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "news")
public record AppProperties(
        List<String> sources,
        Duration requestTimeout,
        Duration collectInitialDelay,
        Duration collectInterval,
        Llm llm
) {
    public record Llm(boolean enabled, String baseUrl, String apiKey, String model) {}
}