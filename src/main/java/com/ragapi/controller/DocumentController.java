package com.ragapi.controller;

import com.ragapi.dto.DocumentDtos;
import com.ragapi.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents", description = "Document upload and management")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Upload a document for indexing",
               description = "Accepts PDF, DOCX, TXT. Processing is async — poll GET /documents/{id} for status.")
    public ResponseEntity<DocumentDtos.DocumentResponse> upload(
            @RequestPart("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        DocumentDtos.DocumentResponse response = documentService.uploadDocument(file);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping
    @Operation(summary = "List all documents")
    public ResponseEntity<DocumentDtos.DocumentListResponse> list() {
        List<DocumentDtos.DocumentResponse> docs = documentService.listDocuments();
        return ResponseEntity.ok(new DocumentDtos.DocumentListResponse(docs, docs.size()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a document by ID")
    public ResponseEntity<DocumentDtos.DocumentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getDocument(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document and all its chunks")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
