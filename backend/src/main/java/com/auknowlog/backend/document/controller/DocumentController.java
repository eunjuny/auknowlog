package com.auknowlog.backend.document.controller;

import com.auknowlog.backend.document.service.DocumentService;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/save-quiz-markdown")
    public ResponseEntity<String> saveQuizAsMarkdown(@RequestBody QuizResponse quizResponse) {
        try {
            String filePath = documentService.saveQuizAsMarkdown(quizResponse);
            return ResponseEntity.ok("Quiz saved successfully to: " + filePath);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to save quiz: " + e.getMessage());
        }
    }
}
