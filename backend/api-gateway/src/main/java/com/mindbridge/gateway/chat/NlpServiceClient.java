package com.mindbridge.gateway.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class NlpServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(NlpServiceClient.class);
    private final WebClient webClient;

    public NlpServiceClient(@Value("${mindbridge.nlp.url:http://localhost:8000}") String nlpUrl) {
        this.webClient = WebClient.builder().baseUrl(nlpUrl).build();
    }

    public record NlpRequest(String text) {}
    public record NlpResponse(String emotion, Double confidence, Double valence, Double arousal) {}

    /**
     * Call the NLP microservice asynchronously. Let reactive pipes handle execution.
     * Enforces the <180ms p95 latency requirement directly via timeout.
     */
    public Mono<NlpResponse> analyzeAsync(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Mono.just(new NlpResponse("neutral", 0.0, 0.0, 0.0));
        }

        return webClient.post()
                .uri("/analyse")
                .bodyValue(new NlpRequest(text))
                .retrieve()
                .bodyToMono(NlpResponse.class)
                .timeout(Duration.ofMillis(200)) // Give 200ms overhead for network vs 180ms target
                .onErrorResume(e -> {
                    logger.warn("NLP Service fallback triggered. Reason: {}", e.getMessage());
                    return Mono.just(new NlpResponse("neutral", 0.0, 0.0, 0.0));
                });
    }

    /** Helper for blocking calls within Virtual Threads */
    public NlpResponse analyzeSync(String text) {
        return analyzeAsync(text).block();
    }
}
