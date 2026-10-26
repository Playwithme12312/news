package com.example.newsservice.service;

import com.example.newsservice.config.AppProperties;
import com.example.newsservice.domain.News;
import com.example.newsservice.domain.Period;
import com.example.newsservice.repository.NewsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryService {

    private final NewsRepository repository;
    private final AppProperties props;
    private final RssFetcher fetcher; // Добавили для живого сбора

    public String summarize(Period period, boolean useLlm) {
        Instant since = Instant.now().minus(period.duration());
        List<News> news = repository.findByPublishedAtAfterOrderByPublishedAtDesc(since);

        if (news.isEmpty()) {
            return "Новостей за период '" + period.name().toLowerCase() + "' не найдено.";
        }

        String fallback = news.stream()
                .limit(100)
                .map(n -> "- [" + n.getTitle() + "](" + n.getLink() + ")")
                .collect(Collectors.joining("\n"));

        AppProperties.Llm llm = props.llm();
        if (!useLlm || llm == null || !llm.enabled()
                || llm.apiKey() == null || llm.apiKey().isBlank()) {
            return fallback;
        }

        try {
            String context = news.stream()
                    .limit(50)
                    .map(n -> "- [" + n.getTitle() + "](" + n.getLink() + "). " + (n.getSummary() == null ? "" : n.getSummary()))
                    .collect(Collectors.joining("\n"));

            String systemPrompt = "Ты — профессиональный российский редактор-полиглот. Твоя задача — сделать краткую сводку новостей. " +
                    "КРИТИЧЕСКИ ВАЖНОЕ ПРАВИЛО: Твой ответ должен быть СТРОГО НА РУССКОМ ЯЗЫКЕ. " +
                    "Даже если все входящие новости на английском языке, ты обязан перевести их суть на русский. " +
                    "Никакого английского текста в твоем ответе быть не должно. " +
                    "Формат: 5-10 пунктов, используй Markdown для ссылок [Текст](ссылка).";


            String userPrompt = "Сделай сводку из этих новостей СТРОГО НА РУССКОМ ЯЗЫКЕ. " +
                    "Переведи английские заголовки на русский. " +
                    "Вот текст новостей:\n" + context;

            return callLlm(systemPrompt, userPrompt, llm);
        } catch (Exception e) {
            log.warn("LLM summary failed, fallback used: {}", e.toString());
            return fallback;
        }
    }

    public String chat(String userMessage, Period period) {
        AppProperties.Llm llm = props.llm();
        if (llm == null || !llm.enabled() || llm.apiKey() == null || llm.apiKey().isBlank()) {
            return "Нейросеть отключена в настройках.";
        }


        Instant since = Instant.now().minus(period.duration());
        List<News> news = repository.findByPublishedAtAfterOrderByPublishedAtDesc(since);

        String dbContext = news.stream()
                .limit(30) // Немного уменьшили, чтобы не перегружать
                .map(n -> "- [" + n.getTitle() + "](" + n.getLink() + ")")
                .collect(Collectors.joining("\n"));

        // 2. Безопасно получаем живые новости (если упадет, чат продолжит работать)
        String liveContext = "";
        try {
            liveContext = fetchLiveContext();
        } catch (Exception e) {
            log.warn("Не удалось получить живые новости для чата: {}", e.getMessage());
        }

        String systemPrompt = "Ты умный аналитик новостей. Отвечай на вопросы пользователя на русском языке. " +
                "Внимательно изучи предоставленный контекст. Если точной цифры нет, но есть новость на эту тему (например, 'курс доллара упал ниже 83'), используй эту информацию. " +
                "Если информации вообще нет, вежливо предложи поискать в другом периоде. " +
                "Обязательно используй Markdown для ссылок [Текст](ссылка).";

        String userPrompt = "=== СВЕЖИЕ НОВОСТИ ИЗ ИНТЕРНЕТА (РЕАЛЬНОЕ ВРЕМЯ) ===\n" + liveContext +
                "\n=== НОВОСТИ ИЗ БАЗЫ ДАННЫХ ===\n" + dbContext +
                "\n\nВопрос пользователя: " + userMessage;

        try {
            return callLlm(systemPrompt, userPrompt, llm);
        } catch (Exception e) {
            log.error("LLM chat failed: {}", e.toString());
            return "Ошибка при обращении к нейросети: " + e.getMessage();
        }
    }

    private String callLlm(String systemPrompt, String userPrompt, AppProperties.Llm llm) {
        Map<String, Object> body = Map.of(
                "model", llm.model(),
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        RestClient client = RestClient.builder()
                .baseUrl(llm.baseUrl())
                .defaultHeader("Authorization", "Bearer " + llm.apiKey())
                .build();

        JsonNode response = client.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) return "";
        JsonNode content = response.at("/choices/0/message/content");
        return content.isMissingNode() ? "" : content.asText();
    }


    private String fetchLiveContext() {
        StringBuilder context = new StringBuilder();
        log.info("ИИ запрашивает данные из интернета в реальном времени...");


        List<String> limitedSources = props.sources();

        for (String url : limitedSources) {
            try {
                byte[] body = fetcher.fetch(url);
                if (body == null) continue;

                com.rometools.rome.feed.synd.SyndFeed feed = new com.rometools.rome.io.SyndFeedInput()
                        .build(new com.rometools.rome.io.XmlReader(new java.io.ByteArrayInputStream(body)));

                context.append("Источник: ").append(url).append("\n");
                feed.getEntries().stream().limit(3).forEach(entry -> {
                    context.append("- [").append(entry.getTitle()).append("](").append(entry.getLink()).append(")\n");
                });
                context.append("\n");
            } catch (Exception e) {
                log.warn("Не удалось скачать живой источник {}: {}", url, e.getMessage());
            }
        }
        return context.toString();
    }
}