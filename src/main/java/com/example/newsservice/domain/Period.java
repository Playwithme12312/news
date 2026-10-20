package com.example.newsservice.domain;

import java.time.Duration;

public enum Period {
    HOUR_2("2h", Duration.ofHours(2)),
    HOUR_6("6h", Duration.ofHours(6)),
    DAY("day", Duration.ofDays(1)),
    WEEK("week", Duration.ofDays(7));

    private final String code;
    private final Duration duration;

    Period(String code, Duration duration) {
        this.code = code;
        this.duration = duration;
    }

    public Duration duration() { return duration; }
    public String code() { return code; }

    public static Period fromString(String value) {
        if (value == null || value.isBlank()) return DAY;
        for (Period p : values()) {
            if (p.code.equalsIgnoreCase(value)) return p;
        }
        return DAY; // По умолчанию - сутки
    }
}