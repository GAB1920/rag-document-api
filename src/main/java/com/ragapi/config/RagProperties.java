package com.ragapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rag")
@Data
public class RagProperties {

    /** Maximum characters per chunk */
    private int chunkSize = 512;

    /** Overlap characters between consecutive chunks */
    private int chunkOverlap = 64;

    /** Number of top-K chunks to retrieve */
    private int topKResults = 5;

    /** Minimum cosine similarity score (0-1) for a chunk to be returned */
    private double similarityThreshold = 0.70;
}
