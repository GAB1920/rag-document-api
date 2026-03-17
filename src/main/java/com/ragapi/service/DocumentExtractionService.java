package com.ragapi.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Extracts plain text from uploaded documents using Apache Tika.
 * Supports PDF, DOCX, TXT, HTML, Markdown, and more.
 */
@Service
@Slf4j
public class DocumentExtractionService {

    private static final Tika TIKA = new Tika();

    /**
     * Extract plain text from an uploaded file.
     *
     * @param file the multipart upload
     * @return extracted plain text content
     */
    public String extractText(MultipartFile file) throws IOException {
        log.info("Extracting text from file: {} ({})", file.getOriginalFilename(), file.getContentType());

        try (InputStream is = file.getInputStream()) {
            Metadata metadata = new Metadata();
            metadata.set("resourceName", file.getOriginalFilename());
            String text = TIKA.parseToString(is, metadata);
            log.info("Extracted {} characters from {}", text.length(), file.getOriginalFilename());
            return text;
        } catch (Exception e) {
            log.error("Text extraction failed for {}: {}", file.getOriginalFilename(), e.getMessage());
            throw new IOException("Failed to extract text: " + e.getMessage(), e);
        }
    }

    /**
     * Detect MIME type of the file without fully parsing it.
     */
    public String detectMimeType(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            return TIKA.detect(is, file.getOriginalFilename());
        }
    }
}
