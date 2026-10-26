package com.example.newsservice.service;

import com.example.newsservice.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupService {

    private final NewsRepository repository;


    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldNews() {
        log.info("Запуск очистки старых новостей из БД...");


        Instant cutoffDate = Instant.now().minus(5, ChronoUnit.DAYS);

        repository.deleteByPublishedAtBefore(cutoffDate);

        log.info("Очистка завершена. Удалены новости старше: {}", cutoffDate);
    }
}