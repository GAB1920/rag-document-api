package com.ragapi.dto;

import com.ragapi.model.Document;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// ─── Document DTOs ────────────────────────────────────────────────────────────

public final class DocumentDtos {

    private DocumentDtos() {}

    @Builder
    public record DocumentResponse(
            UUID id,
            String fileName,
            String fileType,
            long fileSize,
            Document.DocumentStatus status,
            int chunkCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    @Builder
    public record DocumentListResponse(
            List<DocumentResponse> documents,
            int total
    ) {}
}
