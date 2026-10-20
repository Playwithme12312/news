package com.example.newsservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "news",
        uniqueConstraints = @UniqueConstraint(name = "uq_news_url_hash", columnNames = "url_hash"),
        indexes = @Index(name = "ix_news_published_at", columnList = "published_at")
)
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor

//сущность в бд
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String source;

    @Column(nullable = false, length = 1024)
    private String title;

    @Column(nullable = false, length = 2048)
    private String link;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "url_hash", nullable = false, unique = true, length = 64)
    private String urlHash;

    @PrePersist
    void onCreate() {
        if (fetchedAt == null) fetchedAt = Instant.now();
    }
}