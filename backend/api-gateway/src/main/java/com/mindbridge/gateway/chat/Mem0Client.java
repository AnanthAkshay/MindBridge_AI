package com.mindbridge.gateway.chat;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class Mem0Client {
    private static final Logger logger = LoggerFactory.getLogger(Mem0Client.class);
    private final WebClient webClient;

    @Value("${mem0.api.key:${MEM0_API_KEY:}}")
    private String apiKey;

    public Mem0Client(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://api.mem0.ai/v1").build();
    }

    public void addMemoryAsync(Long userId, String content) {
        if (apiKey == null || apiKey.isBlank()) return;

        Map<String, Object> body = Map.of(
            "messages", List.of(Map.of("role", "user", "content", content)),
            "user_id", String.valueOf(userId)
        );

        webClient.post()
            .uri("/memories/")
            .header("Authorization", "Token " + apiKey)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Void.class)
            .doOnError(e -> logger.error("Failed to add Mem0 memory: {}", e.getMessage()))
            .subscribe();
    }

    public Mono<String> searchMemoryAsync(Long userId, String query) {
        if (apiKey == null || apiKey.isBlank()) return Mono.just("");

        Map<String, Object> body = Map.of(
            "query", query,
            "user_id", String.valueOf(userId)
        );

        return webClient.post()
            .uri("/memories/search/")
            .header("Authorization", "Token " + apiKey)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(node -> {
                StringBuilder sb = new StringBuilder();
                if (node.isArray()) {
                    for (JsonNode n : node) {
                        sb.append("- ").append(n.path("memory").asText("")).append("\n");
                    }
                } else if (node.has("results") && node.get("results").isArray()) {
                    for (JsonNode n : node.get("results")) {
                        sb.append("- ").append(n.path("memory").asText("")).append("\n");
                    }
                }
                return sb.toString();
            })
            .doOnError(e -> logger.error("Failed to search Mem0 memory: {}", e.getMessage()))
            .onErrorReturn("");
    }
}
