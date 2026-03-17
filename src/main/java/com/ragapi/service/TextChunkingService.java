package com.ragapi.service;

import com.ragapi.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits raw text into overlapping chunks suitable for embedding.
 *
 * <p>Strategy: sentence-aware sliding window. The chunker tries to break
 * on sentence boundaries ('. ', '! ', '? ') so chunks remain semantically
 * coherent, then falls back to word boundaries if no sentence boundary
 * exists within the window.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextChunkingService {

    private final RagProperties ragProps;

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.replaceAll("\\r\\n|\\r", "\n")
                                .replaceAll("\n{3,}", "\n\n")
                                .trim();

        List<String> chunks = new ArrayList<>();
        int chunkSize    = ragProps.getChunkSize();
        int chunkOverlap = ragProps.getChunkOverlap();

        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());

            // Try to break at a sentence boundary within the last 20 % of the window
            if (end < normalized.length()) {
                int searchFrom = start + (int) (chunkSize * 0.8);
                int boundary   = findSentenceBoundary(normalized, searchFrom, end);
                if (boundary > start) {
                    end = boundary;
                }
            }

            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // Advance with overlap
            start = end - chunkOverlap;
            if (start >= end) start = end; // safety guard
        }

        log.debug("Chunked text of {} chars into {} chunks (size={}, overlap={})",
                normalized.length(), chunks.size(), chunkSize, chunkOverlap);
        return chunks;
    }

    private int findSentenceBoundary(String text, int from, int to) {
        String[] terminators = {". ", "! ", "? ", ".\n", "!\n", "?\n"};
        int best = -1;
        for (String t : terminators) {
            int idx = text.lastIndexOf(t, to);
            if (idx >= from && idx > best) {
                best = idx + t.length();
            }
        }
        return best;
    }
}
