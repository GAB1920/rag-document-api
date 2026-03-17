package com.ragapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class QaDtos {

    private QaDtos() {}

    // ── Request ───────────────────────────────────────────────────────────────

    public record QuestionRequest(
            @NotBlank(message = "Question must not be blank")
            @Size(max = 2000, message = "Question must be at most 2000 characters")
            String question,

            /** Optional: scope retrieval to a specific document */
            UUID documentId,

            /** Optional: associate with a session for conversational context */
            UUID sessionId
    ) {}

    // ── Response ──────────────────────────────────────────────────────────────

    @Builder
    public record QuestionResponse(
            UUID id,
            String question,
            String answer,
            List<SourceChunk> sources,
            String modelUsed,
            int tokensUsed,
            int latencyMs,
            OffsetDateTime createdAt
    ) {}

    @Builder
    public record SourceChunk(
            UUID chunkId,
            UUID documentId,
            String documentName,
            int chunkIndex,
            String content,
            double similarityScore
    ) {}

    @Builder
    public record QaHistoryResponse(
            UUID id,
            UUID sessionId,
            String question,
            String answer,
            List<UUID> sourceChunkIds,
            String modelUsed,
            int tokensUsed,
            OffsetDateTime createdAt
    ) {}

    @Builder
    public record QaHistoryPage(
            List<QaHistoryResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
