package com.ragapi.controller;

import com.ragapi.dto.QaDtos;
import com.ragapi.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/qa")
@RequiredArgsConstructor
@Tag(name = "Q&A", description = "Ask questions against indexed documents using RAG")
public class QaController {

    private final RagService ragService;

    @PostMapping("/ask")
    @Operation(
        summary = "Ask a question",
        description = "Embeds the question, retrieves similar chunks, and generates an answer via GPT-4o."
    )
    public ResponseEntity<QaDtos.QuestionResponse> ask(
            @Valid @RequestBody QaDtos.QuestionRequest request) {
        return ResponseEntity.ok(ragService.ask(request));
    }

    @GetMapping("/history")
    @Operation(summary = "Get Q&A history (paginated)")
    public ResponseEntity<QaDtos.QaHistoryPage> history(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ragService.getHistory(page, size));
    }
}
