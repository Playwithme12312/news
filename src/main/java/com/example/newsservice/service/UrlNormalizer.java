package com.example.newsservice.service;

import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class UrlNormalizer {

    private static final Set<String> TRACKING_KEYS = Set.of("fbclid", "gclid", "yclid", "igshid");
    private static final List<String> TRACKING_PREFIXES = List.of("utm_", "mc_", "pk_");

    public String normalize(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);

            MultiValueMap<String, String> filtered = new LinkedMultiValueMap<>();
            UriComponentsBuilder.fromUri(uri).build().getQueryParams().forEach((k, values) -> {
                if (!isTracking(k.toLowerCase(Locale.ROOT))) values.forEach(v -> filtered.add(k, v));
            });

            String path = uri.getPath();
            if (path != null && path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            var builder = UriComponentsBuilder.newInstance()
                    .scheme(scheme).host(host).path(path).queryParams(filtered);
            if (uri.getPort() > 0) builder.port(uri.getPort());
            return builder.build().toUriString();
        } catch (Exception e) {
            return url.trim();
        }
    }

    private boolean isTracking(String key) {
        return TRACKING_KEYS.contains(key) || TRACKING_PREFIXES.stream().anyMatch(key::startsWith);
    }

    public String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}