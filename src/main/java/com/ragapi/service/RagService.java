package com.ragapi.service;

import com.ragapi.config.RagProperties;
import com.ragapi.dto.QaDtos;
import com.ragapi.model.DocumentChunk;
import com.ragapi.model.QaHistory;
import com.ragapi.repository.DocumentChunkRepository;
import com.ragapi.repository.DocumentRepository;
import com.ragapi.repository.QaHistoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a helpful assistant that answers questions based strictly on the provided context.

            Instructions:
            - Answer ONLY based on the context below.
            - If the context does not contain enough information to answer, say so clearly.
            - Be concise, accurate, and cite which part of the context supports your answer.
            - Do not hallucinate or invent information not present in the context.

            Context:
            ---
            %s
            ---
            """;

    private final OpenAiService            openAiService;
    private final DocumentChunkRepository  chunkRepository;
    private final DocumentRepository       documentRepository;
    private final QaHistoryRepository      qaHistoryRepository;
    private final RagProperties            ragProps;

    // ── Ask ───────────────────────────────────────────────────────────────────

    @Transactional
    public QaDtos.QuestionResponse ask(QaDtos.QuestionRequest request) {
        long start = Instant.now().toEpochMilli();
        log.info("Processing question: {}", request.question());

        // 1. Validate document scope if provided
        if (request.documentId() != null && !documentRepository.existsById(request.documentId())) {
            throw new EntityNotFoundException("Document not found: " + request.documentId());
        }

        // 2. Embed the question
        float[] queryEmbedding = openAiService.embed(request.question());
        String  pgVectorLiteral = toPgVectorString(queryEmbedding);

        // 3. Retrieve top-K similar chunks
        List<DocumentChunk> relevantChunks = retrieveChunks(
                pgVectorLiteral, request.documentId());

        if (relevantChunks.isEmpty()) {
            log.warn("No relevant chunks found for question");
            return buildNoContextResponse(request);
        }

        log.info("Retrieved {} relevant chunks", relevantChunks.size());

        // 4. Build context string
        String context = buildContext(relevantChunks);

        // 5. Generate answer
        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.formatted(context);
        var chatResponse = openAiService.chat(systemPrompt, request.question());
        String answer = chatResponse.choices().getFirst().message().content();

        long latency = Instant.now().toEpochMilli() - start;

        // 6. Persist Q&A history
        UUID[] chunkIds = relevantChunks.stream()
                .map(DocumentChunk::getId)
                .toArray(UUID[]::new);

        QaHistory history = QaHistory.builder()
                .sessionId(request.sessionId())
                .question(request.question())
                .answer(answer)
                .sourceChunkIds(chunkIds)
                .modelUsed(chatResponse.id())
                .tokensUsed(chatResponse.usage().totalTokens())
                .latencyMs((int) latency)
                .build();

        history = qaHistoryRepository.save(history);

        // 7. Build source references with similarity scores
        List<QaDtos.SourceChunk> sources = buildSourceChunks(relevantChunks, queryEmbedding);

        return QaDtos.QuestionResponse.builder()
                .id(history.getId())
                .question(request.question())
                .answer(answer)
                .sources(sources)
                .modelUsed(chatResponse.id())
                .tokensUsed(chatResponse.usage().totalTokens())
                .latencyMs((int) latency)
                .createdAt(history.getCreatedAt())
                .build();
    }

    // ── History ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public QaDtos.QaHistoryPage getHistory(int page, int size) {
        Page<QaHistory> result = qaHistoryRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, size));

        List<QaDtos.QaHistoryResponse> items = result.getContent().stream()
                .map(this::toHistoryResponse)
                .toList();

        return QaDtos.QaHistoryPage.builder()
                .items(items)
                .page(page)
                .size(size)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<DocumentChunk> retrieveChunks(String pgVector, UUID documentId) {
        if (documentId != null) {
            return chunkRepository.findSimilarChunksInDocument(
                    documentId, pgVector,
                    ragProps.getTopKResults(),
                    ragProps.getSimilarityThreshold());
        }
        return chunkRepository.findSimilarChunks(
                pgVector,
                ragProps.getTopKResults(),
                ragProps.getSimilarityThreshold());
    }

    private String buildContext(List<DocumentChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk c = chunks.get(i);
            sb.append("[Source ").append(i + 1).append("] ")
              .append("Document: ").append(c.getDocument().getFileName())
              .append(" | Chunk #").append(c.getChunkIndex()).append("\n")
              .append(c.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private List<QaDtos.SourceChunk> buildSourceChunks(List<DocumentChunk> chunks, float[] queryEmbedding) {
        return chunks.stream().map(c -> {
            double score = cosineSimilarity(queryEmbedding,
                    c.getEmbedding() != null ? c.getEmbedding().toArray() : new float[0]);
            return QaDtos.SourceChunk.builder()
                    .chunkId(c.getId())
                    .documentId(c.getDocument().getId())
                    .documentName(c.getDocument().getFileName())
                    .chunkIndex(c.getChunkIndex())
                    .content(c.getContent())
                    .similarityScore(Math.round(score * 10000.0) / 10000.0)
                    .build();
        }).collect(Collectors.toList());
    }

    private QaDtos.QuestionResponse buildNoContextResponse(QaDtos.QuestionRequest request) {
        return QaDtos.QuestionResponse.builder()
                .question(request.question())
                .answer("I could not find relevant information in the uploaded documents to answer your question.")
                .sources(List.of())
                .modelUsed("N/A")
                .tokensUsed(0)
                .latencyMs(0)
                .build();
    }

    private QaDtos.QaHistoryResponse toHistoryResponse(QaHistory h) {
        return QaDtos.QaHistoryResponse.builder()
                .id(h.getId())
                .sessionId(h.getSessionId())
                .question(h.getQuestion())
                .answer(h.getAnswer())
                .sourceChunkIds(h.getSourceChunkIds() != null
                        ? Arrays.asList(h.getSourceChunkIds()) : List.of())
                .modelUsed(h.getModelUsed())
                .tokensUsed(h.getTokensUsed())
                .createdAt(h.getCreatedAt())
                .build();
    }

    /** Convert float[] to pgvector literal string: [0.1,0.2,...] */
    private String toPgVectorString(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(v[i]);
        }
        return sb.append("]").toString();
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot   += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return (normA == 0 || normB == 0) ? 0.0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
