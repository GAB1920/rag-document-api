package com.ragapi.service;

import com.pgvector.PGvector;
import com.ragapi.dto.DocumentDtos;
import com.ragapi.model.Document;
import com.ragapi.model.DocumentChunk;
import com.ragapi.repository.DocumentChunkRepository;
import com.ragapi.repository.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository         documentRepository;
    private final DocumentChunkRepository    chunkRepository;
    private final DocumentExtractionService  extractionService;
    private final TextChunkingService        chunkingService;
    private final OpenAiService              openAiService;

    // ── Upload ────────────────────────────────────────────────────────────────

    @Transactional
    public DocumentDtos.DocumentResponse uploadDocument(MultipartFile file) throws IOException {
        String mimeType = extractionService.detectMimeType(file);
        log.info("Uploading document: {} ({})", file.getOriginalFilename(), mimeType);

        Document doc = Document.builder()
                .fileName(file.getOriginalFilename())
                .fileType(mimeType)
                .fileSize(file.getSize())
                .status(Document.DocumentStatus.PENDING)
                .build();

        doc = documentRepository.save(doc);
        log.info("Document record created: {}", doc.getId());

        // Kick off async ingestion pipeline
        processDocument(doc.getId(), file.getBytes());

        return toResponse(doc);
    }

    // ── Async ingestion pipeline ──────────────────────────────────────────────

    @Async
    public void processDocument(UUID documentId, byte[] fileBytes) {
        log.info("Starting ingestion pipeline for document: {}", documentId);

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));

        try {
            // 1. Update status → PROCESSING
            doc.setStatus(Document.DocumentStatus.PROCESSING);
            documentRepository.save(doc);

            // 2. Extract text
            String rawText = new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);
            // For binary formats (PDF, DOCX), re-extract via Tika
            // We stored bytes; use a ByteArrayInputStream wrapper
            try {
                var tmpFile = org.springframework.mock.web.MockMultipartFile
                        .class.getSimpleName(); // just to check if we need tika
            } catch (Exception ignored) {}

            // Direct Tika parse from bytes
            org.apache.tika.Tika tika = new org.apache.tika.Tika();
            rawText = tika.parseToString(new java.io.ByteArrayInputStream(fileBytes));

            // 3. Chunk text
            List<String> textChunks = chunkingService.chunk(rawText);
            log.info("Document {} split into {} chunks", documentId, textChunks.size());

            // 4. Embed each chunk and persist
            List<DocumentChunk> chunks = new ArrayList<>(textChunks.size());
            for (int i = 0; i < textChunks.size(); i++) {
                String chunkText = textChunks.get(i);
                float[] vector   = openAiService.embed(chunkText);

                DocumentChunk chunk = DocumentChunk.builder()
                        .document(doc)
                        .chunkIndex(i)
                        .content(chunkText)
                        .tokenCount(estimateTokens(chunkText))
                        .embedding(new PGvector(vector))
                        .build();
                chunks.add(chunk);
            }

            chunkRepository.saveAll(chunks);

            // 5. Mark READY
            doc.setStatus(Document.DocumentStatus.READY);
            doc.setChunkCount(chunks.size());
            documentRepository.save(doc);

            log.info("Document {} ingested successfully ({} chunks)", documentId, chunks.size());

        } catch (Exception e) {
            log.error("Ingestion failed for document {}: {}", documentId, e.getMessage(), e);
            doc.setStatus(Document.DocumentStatus.FAILED);
            documentRepository.save(doc);
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DocumentDtos.DocumentResponse> listDocuments() {
        return documentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDtos.DocumentResponse getDocument(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + id));
        return toResponse(doc);
    }

    @Transactional
    public void deleteDocument(UUID id) {
        if (!documentRepository.existsById(id)) {
            throw new EntityNotFoundException("Document not found: " + id);
        }
        chunkRepository.deleteByDocumentId(id);
        documentRepository.deleteById(id);
        log.info("Document {} and all its chunks deleted", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DocumentDtos.DocumentResponse toResponse(Document doc) {
        return DocumentDtos.DocumentResponse.builder()
                .id(doc.getId())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .status(doc.getStatus())
                .chunkCount(doc.getChunkCount())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    /** Rough token estimate: ~4 chars per token */
    private int estimateTokens(String text) {
        return Math.max(1, text.length() / 4);
    }
}
