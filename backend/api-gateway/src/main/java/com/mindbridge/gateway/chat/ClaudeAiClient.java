package com.mindbridge.gateway.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Enterprise-grade client for Anthropic's Claude 3 API via SSE.
 * Generates an empathetic, continuous text stream dynamically guided by user
 * sentiment scoring, DB memory insights, and rigorous system prompts.
 */
@Service
public class ClaudeAiClient {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeAiClient.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api.key:}")
    private String apiKey;

    public ClaudeAiClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl("https://api.anthropic.com/v1").build();
        this.objectMapper = objectMapper;
    }

    /**
     * Deep integration method orchestrating system constraints, context memory, and the active query.
     */
    public Flux<String> streamEmpatheticResponse(
            String userMessage,
            String emotion,
            Double valence,
            MemoryService.MemoryInsight memory,
            List<Map<String, String>> previousMessages) {

        // 1. Safety Filter Intercept Layer
        if (containsSelfHarmIntent(userMessage)) {
            return Flux.just("I hear how much pain you are in. Please know that you don't have to carry this alone. Help is available right now. Please call 988 (National Suicide Prevention Lifeline) or reach out to someone you trust. I am an AI, but your life is incredibly important, and I truly want you to be safe.");
        }

        // 2. Intelligent Prompt Compilation
        String systemPrompt = buildSystemPrompt(emotion, valence, memory);

        // Fallback robust local stream if API key is not configured securely yet
        if (apiKey == null || apiKey.isBlank()) {
            logger.warn("ANTHROPIC_API_KEY missing. Falling back to local deterministic stream.");
            return simulateLocalReactiveStream(userMessage, systemPrompt);
        }

        // 3. Anthropic API Request construction
        Map<String, Object> requestBody = Map.of(
                "model", "claude-3-sonnet-20240229",
                "max_tokens", 800,
                "system", systemPrompt,
                "stream", true,
                "messages", compileConversationHistory(previousMessages, userMessage)
        );

        return webClient.post()
                .uri("/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(ServerSentEvent.class)
                .filter(sse -> "content_block_delta".equals(sse.event()))
                .mapNotNull(sse -> {
                    try {
                        JsonNode node = objectMapper.readTree(String.valueOf(sse.data()));
                        return node.path("delta").path("text").asText("");
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(text -> !text.isEmpty());
    }

    private String buildSystemPrompt(String emotion, Double valence, MemoryService.MemoryInsight memory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are MindBridge AI, an elite, highly empathetic mental wellness companion. ");
        prompt.append("You utilize principles of active listening, validation, and gentle CBT/DBT frameworks.\n\n");
        prompt.append("CRITICAL PARAMETERS:\n");
        prompt.append("1. NEVER attempt to medically diagnose the user or prescribe medication. You are a supportive companion, not a doctor.\n");
        prompt.append("2. Validate first. Always make the user feel heard and understood before offering solutions.\n");
        prompt.append("3. Keep responses strictly concise (1-3 brief paragraphs maximum). Long text walls are overwhelming in chat formats.\n");
        prompt.append("4. Use Socratic questioning to gently guide the user to their own realizations.\n");
        prompt.append("5. If the user mentions abuse, self-harm, or severe danger, prioritize safety immediately. Offer grounding techniques and gently recommend professional crisis support (like 988).\n\n");

        prompt.append("CURRENT PSYCHOLOGICAL TELEMETRY:\n");
        prompt.append("- Dominant Detected Emotion: ").append(emotion).append("\n");
        prompt.append("- State Valence: ").append(valence).append("\n\n");

        if (memory != null && memory.triggers() != null && !memory.triggers().isEmpty()) {
            prompt.append("CROSS-SESSION MEMORY (Use this to show deep contextual continuity implicitly):\n");
            prompt.append("- Long-term trend: ").append(memory.trend()).append("\n");
            prompt.append("- Known behavioral triggers: ").append(String.join(", ", memory.triggers())).append("\n");
            prompt.append("- Recent contextual summary: ").append(memory.recentSummary()).append("\n");
            prompt.append("(Instruction: Do not randomly quote this memory, but weave it intelligently to inform how you respond, showing you remember them.)\n");
        }

        return prompt.toString();
    }

    private List<Map<String, String>> compileConversationHistory(List<Map<String, String>> previousMessages, String latestUserMessage) {
        // Appends the current message onto the trailing conversation history mapped for Claude
        // (Implementation omitted to keep example lean, returning just latest for now)
        return List.of(Map.of("role", "user", "content", latestUserMessage));
    }

    private boolean containsSelfHarmIntent(String text) {
        String lower = text.toLowerCase();
        return lower.matches(".*(kill myself|end it all|want to die|don't want to live|better off dead|hurt myself|suicide|no reason to live).*");
    }

    private Flux<String> simulateLocalReactiveStream(String userMessage, String systemContext) {
        String lowerMsg = userMessage.toLowerCase();
        String fallbackResponse = "I hear what you are saying, and I appreciate you sharing that with me. " 
            + "It takes real courage to open up. Based on what we've previously touched upon, I understand this can be heavy. "
            + "What does support look like for you in this exact moment?";
            
        if (lowerMsg.contains("overwhelmed") || lowerMsg.contains("stress")) {
            fallbackResponse = "I hear that you're feeling incredibly overwhelmed right now. It is completely normal to feel paralyzed when everything piles up. Let's take a deep breath together. What is the very first, smallest thing we can address?";
        } else if (lowerMsg.contains("sad") || lowerMsg.contains("crying") || lowerMsg.contains("depressed")) {
            fallbackResponse = "I'm so sorry you're feeling this weight right now. It's perfectly okay to feel sad, and you don't have to navigate it alone. I'm here with you. Do you want to talk about what brought this on?";
        } else if (lowerMsg.contains("anxious") || lowerMsg.contains("panic") || lowerMsg.contains("worry")) {
            fallbackResponse = "Anxiety can feel so intense and all-consuming. I want you to know you are safe right now. Try to feel your feet on the ground. Can you name three things you can see around you?";
        } else if (lowerMsg.contains("lonely") || lowerMsg.contains("alone") || lowerMsg.contains("isolated")) {
            fallbackResponse = "Loneliness is a very heavy feeling to carry. Please know that even in this digital space, you are heard and your feelings matter deeply. Have you been feeling this way for a while?";
        } else if (lowerMsg.contains("angry") || lowerMsg.contains("mad") || lowerMsg.contains("frustrated")) {
            fallbackResponse = "It's completely valid to feel angry right now. Anger is often a sign that a boundary has been crossed or something feels unfair. It's okay to let that out here. What is the core of this frustration?";
        } else if (lowerMsg.contains("sleep") || lowerMsg.contains("tired") || lowerMsg.contains("exhausted")) {
            fallbackResponse = "It sounds like you are physically and emotionally drained. Rest is so important for your mental health. Try not to push yourself too hard today. What is one small way you can be gentle with yourself right now?";
        } else if (lowerMsg.contains("happy") || lowerMsg.contains("good") || lowerMsg.contains("great") || lowerMsg.contains("excited")) {
            fallbackResponse = "That is wonderful to hear! It's so important to recognize and celebrate these positive moments. What do you think is contributing to this good feeling?";
        }

        String[] chunks = fallbackResponse.split(" ");
        return Flux.interval(Duration.ofMillis(45))
                .take(chunks.length)
                .map(i -> chunks[i.intValue()] + " ");
    }
}
