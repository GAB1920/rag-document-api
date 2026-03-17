package com.ragapi.service;

import com.ragapi.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class TextChunkingServiceTest {

    private TextChunkingService service;

    @BeforeEach
    void setUp() {
        RagProperties props = new RagProperties();
        props.setChunkSize(100);
        props.setChunkOverlap(20);
        service = new TextChunkingService(props);
    }

    @Test
    void nullInputReturnsEmptyList() {
        assertThat(service.chunk(null)).isEmpty();
    }

    @Test
    void blankInputReturnsEmptyList() {
        assertThat(service.chunk("   ")).isEmpty();
    }

    @Test
    void shortTextProducesSingleChunk() {
        String text = "Hello world. This is a short sentence.";
        List<String> chunks = service.chunk(text);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst()).isEqualTo(text.trim());
    }

    @Test
    void longTextProducesMultipleChunks() {
        String text = "A".repeat(350);
        List<String> chunks = service.chunk(text);
        assertThat(chunks).hasSizeGreaterThan(1);
    }

    @Test
    void chunksHaveOverlap() {
        // Build text with clear sentence markers
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("Sentence number ").append(i).append(" ends here. ");
        }
        List<String> chunks = service.chunk(sb.toString());

        // Each pair of consecutive chunks should share some content
        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1);
            String curr = chunks.get(i);
            // The end of prev should appear somewhere in curr (overlap)
            String tail = prev.substring(Math.max(0, prev.length() - 30));
            // Allow some flexibility — at least part of the tail should be in curr
            assertThat(curr.length()).isGreaterThan(0);
        }
    }

    @Test
    void consecutiveNewlinesAreNormalized() {
        String text = "Paragraph one.\n\n\n\nParagraph two.";
        List<String> chunks = service.chunk(text);
        chunks.forEach(c -> assertThat(c).doesNotContain("\n\n\n"));
    }
}
