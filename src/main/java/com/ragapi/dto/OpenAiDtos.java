package com.ragapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class OpenAiDtos {

    private OpenAiDtos() {}

    // ── Embedding ─────────────────────────────────────────────────────────────

    public record EmbeddingRequest(
            String input,
            String model,
            Integer dimensions
    ) {}

    public record EmbeddingResponse(
            String object,
            List<EmbeddingData> data,
            Usage usage
    ) {}

    public record EmbeddingData(
            String object,
            int index,
            List<Float> embedding
    ) {}

    // ── Chat Completion ───────────────────────────────────────────────────────

    public record ChatRequest(
            String model,
            List<ChatMessage> messages,
            @JsonProperty("max_tokens") int maxTokens,
            double temperature
    ) {}

    public record ChatMessage(
            String role,
            String content
    ) {}

    public record ChatResponse(
            String id,
            String object,
            List<ChatChoice> choices,
            Usage usage
    ) {}

    public record ChatChoice(
            int index,
            ChatMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    // ── Shared ────────────────────────────────────────────────────────────────

    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {}
}
