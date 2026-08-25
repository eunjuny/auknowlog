package com.auknowlog.backend.embedding.service;

import com.auknowlog.backend.embedding.repository.QuestionVectorRepository;
import com.auknowlog.backend.question.repository.QuestionHistoryRepository;
import com.auknowlog.backend.question.service.QuestionHistoryService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SemanticDuplicateService {

    private static final double DUPLICATE_THRESHOLD = 0.90;

    private final EmbeddingService embeddingService;
    private final QuestionVectorRepository questionVectorRepository;
    private final QuestionHistoryService questionHistoryService;
    private final QuestionHistoryRepository questionHistoryRepository;

    public SemanticDuplicateService(EmbeddingService embeddingService,
                                    QuestionVectorRepository questionVectorRepository,
                                    QuestionHistoryService questionHistoryService,
                                    QuestionHistoryRepository questionHistoryRepository) {
        this.embeddingService = embeddingService;
        this.questionVectorRepository = questionVectorRepository;
        this.questionHistoryService = questionHistoryService;
        this.questionHistoryRepository = questionHistoryRepository;
    }

    public SemanticCheck check(String questionText) {
        Optional<EmbeddingResult> embedding = embeddingService.embed(questionText);
        if (embedding.isEmpty()) {
            return SemanticCheck.notAvailable();
        }

        Optional<QuestionVectorRepository.SimilarQuestion> similar = questionVectorRepository
                .findMostSimilar(embedding.get(), DUPLICATE_THRESHOLD);
        return similar.map(value -> new SemanticCheck(true, value.similarity(), embedding))
                .orElseGet(() -> new SemanticCheck(false, 0, embedding));
    }

    public void indexSavedQuestion(String questionText, SemanticCheck check) {
        check.embedding().ifPresent(embedding -> questionHistoryRepository
                .findByQuestionHash(questionHistoryService.generateHash(questionText))
                .ifPresent(question -> questionVectorRepository.upsert(question.getId(), embedding)));
    }

    public record SemanticCheck(boolean duplicate, double similarity, Optional<EmbeddingResult> embedding) {
        public static SemanticCheck notAvailable() {
            return new SemanticCheck(false, 0, Optional.empty());
        }
    }
}
