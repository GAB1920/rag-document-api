package com.ragapi.repository;

import com.ragapi.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(UUID documentId);

    /**
     * Find the top-K most similar chunks using cosine distance (pgvector).
     * Filters by similarity threshold and only returns chunks from READY documents.
     */
    @Query(value = """
            SELECT dc.*
            FROM document_chunks dc
            JOIN documents d ON d.id = dc.document_id
            WHERE d.status = 'READY'
              AND dc.embedding IS NOT NULL
              AND 1 - (dc.embedding <=> CAST(:embedding AS vector)) >= :threshold
            ORDER BY dc.embedding <=> CAST(:embedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<DocumentChunk> findSimilarChunks(
            @Param("embedding") String embedding,
            @Param("topK") int topK,
            @Param("threshold") double threshold
    );

    /**
     * Similarity search scoped to a specific document.
     */
    @Query(value = """
            SELECT dc.*
            FROM document_chunks dc
            WHERE dc.document_id = :documentId
              AND dc.embedding IS NOT NULL
              AND 1 - (dc.embedding <=> CAST(:embedding AS vector)) >= :threshold
            ORDER BY dc.embedding <=> CAST(:embedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<DocumentChunk> findSimilarChunksInDocument(
            @Param("documentId") UUID documentId,
            @Param("embedding") String embedding,
            @Param("topK") int topK,
            @Param("threshold") double threshold
    );

    long countByDocumentId(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
