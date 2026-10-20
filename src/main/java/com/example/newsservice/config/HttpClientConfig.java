package com.example.newsservice.config;
//настройка Rest клиента
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient rssClient(AppProperties props) {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(props.requestTimeout())
                .withReadTimeout(props.requestTimeout());
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .defaultHeader("User-Agent", "news-collector/1.0")
                .build();
    }
}