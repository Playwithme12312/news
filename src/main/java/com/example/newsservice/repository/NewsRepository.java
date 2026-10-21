package com.example.newsservice.repository;


import org.springframework.transaction.annotation.Transactional;
import com.example.newsservice.domain.News;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
//интерфейс
public interface NewsRepository extends JpaRepository<News, Long> {
    boolean existsByUrlHash(String urlHash);
    List<News> findByPublishedAtAfterOrderByPublishedAtDesc(Instant since);

    @Transactional
    void deleteByPublishedAtBefore(Instant date);

}
