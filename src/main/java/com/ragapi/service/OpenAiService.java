package com.ragapi.service;

import com.ragapi.config.OpenAiProperties;
import com.ragapi.dto.OpenAiDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiService {

    private final WebClient openAiWebClient;
    private final OpenAiProperties props;

    /**
     * Generate a single embedding vector for the given text.
     *
     * @param text input text (will be truncated to model limits if needed)
     * @return float array representing the embedding
     */
    public float[] embed(String text) {
        log.debug("Generating embedding for text of length {}", text.length());

        var request = new OpenAiDtos.EmbeddingRequest(text, props.getEmbeddingModel(), props.getEmbeddingDimensions());

        try {
            var response = openAiWebClient.post()
                    .uri("/embeddings")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiDtos.EmbeddingResponse.class)
                    .block();

            if (response == null || response.data().isEmpty()) {
                throw new IllegalStateException("Empty embedding response from OpenAI");
            }

            List<Float> embedding = response.data().getFirst().embedding();
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i);
            }
            log.debug("Embedding generated: {} dimensions", result.length);
            return result;

        } catch (WebClientResponseException e) {
            log.error("OpenAI embedding error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
        }
    }

    /**
     * Chat completion with a system prompt and user message.
     *
     * @param systemPrompt the system instructions
     * @param userMessage  the user's question/query
     * @return ChatResponse containing the model's answer and usage stats
     */
    public OpenAiDtos.ChatResponse chat(String systemPrompt, String userMessage) {
        log.debug("Sending chat request to model: {}", props.getChatModel());

        var messages = List.of(
                new OpenAiDtos.ChatMessage("system", systemPrompt),
                new OpenAiDtos.ChatMessage("user", userMessage)
        );

        var request = new OpenAiDtos.ChatRequest(
                props.getChatModel(),
                messages,
                props.getMaxTokens(),
                props.getTemperature()
        );

        try {
            var response = openAiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(OpenAiDtos.ChatResponse.class)
                    .block();

            if (response == null || response.choices().isEmpty()) {
                throw new IllegalStateException("Empty chat response from OpenAI");
            }

            log.debug("Chat response received. Tokens used: {}", response.usage().totalTokens());
            return response;

        } catch (WebClientResponseException e) {
            log.error("OpenAI chat error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to get chat completion: " + e.getMessage(), e);
        }
    }
}
