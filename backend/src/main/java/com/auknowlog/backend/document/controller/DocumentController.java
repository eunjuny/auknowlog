package com.auknowlog.backend.document.controller;

import com.auknowlog.backend.document.service.DocumentService;
import com.auknowlog.backend.document.service.NotionService;
import com.auknowlog.backend.document.service.GitService;
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
    private final NotionService notionService;
    private final GitService gitService;

    public DocumentController(DocumentService documentService, GeminiService geminiService, 
                             NotionService notionService, GitService gitService) {
        this.documentService = documentService;
        this.geminiService = geminiService;
        this.notionService = notionService;
        this.gitService = gitService;
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
            String markdown = geminiService.renderQuizMarkdownLocally(payload);
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
            String parentPageId = payload.get("parentPageId") != null ? String.valueOf(payload.get("parentPageId")) : null;
            String databaseId = payload.get("databaseId") != null ? String.valueOf(payload.get("databaseId")) : null;
            String databaseTitleProperty = payload.get("databaseTitleProperty") != null ? String.valueOf(payload.get("databaseTitleProperty")) : null;

            String markdown = geminiService.renderQuizMarkdownLocally(payload);
            String result = notionService.createPageWithMarkdown(title, markdown, parentPageId, databaseId, databaseTitleProperty);
            return ResponseEntity.ok("노션 저장 완료: " + (result == null ? "(no response)" : result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("노션 저장 실패: " + e.getMessage());
        }
    }

    @PostMapping(value = "/save-quiz-git", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> saveQuizToGit(@RequestBody java.util.Map<String, Object> payload) {
        try {
            String title = String.valueOf(payload.getOrDefault("quizTitle", "퀴즈 결과"));
            String markdown = geminiService.renderQuizMarkdownLocally(payload);
            String filePath = documentService.saveMarkdownContent(title, markdown);
            String msg = gitService.commitAndPush(filePath, "chore: save quiz markdown (" + title + ")", "notes", "main");
            return ResponseEntity.ok("Git 저장 완료: " + msg + " | " + filePath);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Git 저장 실패: " + e.getMessage());
        }
    }
}
