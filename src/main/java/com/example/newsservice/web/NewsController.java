package com.example.newsservice.web;

import com.example.newsservice.domain.News;
import com.example.newsservice.domain.Period;
import com.example.newsservice.repository.NewsRepository;
import com.example.newsservice.service.RssCollector;
import com.example.newsservice.service.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class NewsController {

    private final RssCollector collector;
    private final SummaryService summaryService;
    private final NewsRepository repository;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/collect")
    public Map<String, Integer> collect() {
        return collector.collectAll();
    }

    @GetMapping(value = "/summary", produces = MediaType.TEXT_PLAIN_VALUE)
    public String summary(
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(defaultValue = "true") boolean llm) {
        return summaryService.summarize(Period.fromString(period), llm);
    }

    @GetMapping(value = "/summary", produces = MediaType.APPLICATION_JSON_VALUE, params = "format=json")
    public Map<String, String> summaryJson(
            @RequestParam(defaultValue = "day") String period,
            @RequestParam(defaultValue = "true") boolean llm) {
        return Map.of(
                "period", period,
                "summary", summaryService.summarize(Period.fromString(period), llm)
        );
    }

    @GetMapping(value = "/api/news", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<News> getNews(@RequestParam(defaultValue = "day") String period) {
        Instant since = Instant.now().minus(Period.fromString(period).duration());
        return repository.findByPublishedAtAfterOrderByPublishedAtDesc(since);
    }

    @PostMapping(value = "/api/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");

        String periodStr = request.getOrDefault("period", "day");


        String reply = summaryService.chat(message, Period.fromString(periodStr));

        return Map.of("reply", reply);
    }
}