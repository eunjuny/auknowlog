package com.auknowlog.backend.document.controller;

import com.auknowlog.backend.document.service.DocumentService;
import com.auknowlog.backend.quiz.dto.QuizResponse;
import com.auknowlog.backend.quiz.service.GeminiService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final GeminiService geminiService;

    public DocumentController(DocumentService documentService, GeminiService geminiService) {
        this.documentService = documentService;
        this.geminiService = geminiService;
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

    @PostMapping(value = "/save-quiz-markdown-raw", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> saveQuizMarkdownRaw(@RequestBody java.util.Map<String, Object> payload) {
        try {
            String title = String.valueOf(payload.getOrDefault("quizTitle", "퀴즈 결과"));
            String markdown = geminiService.renderQuizMarkdownLocally(payload).block();
            String filePath = documentService.saveMarkdownContent(title, markdown);
            return ResponseEntity.ok("Quiz saved successfully to: " + filePath);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to save quiz: " + e.getMessage());
        }
    }

    @PostMapping(value = "/save-quiz-notion", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> saveQuizToNotion(@RequestBody java.util.Map<String, Object> payload) {
        try {
            String title = String.valueOf(payload.getOrDefault("quizTitle", "퀴즈 결과"));
            String markdown = geminiService.renderQuizMarkdownLocally(payload).block();
            int length = (markdown == null) ? 0 : markdown.length();
            // TODO: 실제 Notion API 저장은 향후 구현
            return ResponseEntity.ok("노션 저장(임시) 완료: " + title + " (" + length + " chars)");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("노션 저장 실패: " + e.getMessage());
        }
    }
}
