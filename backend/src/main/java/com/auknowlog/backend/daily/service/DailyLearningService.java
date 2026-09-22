package com.auknowlog.backend.daily.service;

import com.auknowlog.backend.daily.dto.*;
import com.auknowlog.backend.daily.entity.DailyLearning;
import com.auknowlog.backend.daily.entity.DailyLearningTrack;
import com.auknowlog.backend.daily.repository.DailyLearningRepository;
import com.auknowlog.backend.learning.entity.LearningQuiz;
import com.auknowlog.backend.learning.repository.LearningQuizRepository;
import com.auknowlog.backend.learning.service.LearningService;
import com.auknowlog.backend.quiz.dto.QuizRequest;
import com.auknowlog.backend.quiz.dto.QuizViewResponse;
import com.auknowlog.backend.quiz.service.QuizGenerationService;
import com.auknowlog.backend.source.dto.SourceCreateRequest;
import com.auknowlog.backend.source.dto.SourceCreateResponse;
import com.auknowlog.backend.source.dto.SourcePreviewResponse;
import com.auknowlog.backend.source.entity.SourceDocument;
import com.auknowlog.backend.source.entity.SourceType;
import com.auknowlog.backend.source.repository.SourceDocumentRepository;
import com.auknowlog.backend.source.service.SourcePreviewService;
import com.auknowlog.backend.source.service.SourceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class DailyLearningService {
    private final DailyLearningRepository repository;
    private final DailyArticleFeedService articleFeedService;
    private final SourcePreviewService sourcePreviewService;
    private final SourceService sourceService;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final DailyLearningAiService aiService;
    private final QuizGenerationService quizGenerationService;
    private final LearningService learningService;
    private final LearningQuizRepository learningQuizRepository;
    private final ObjectMapper objectMapper;

    public DailyLearningService(DailyLearningRepository repository, DailyArticleFeedService articleFeedService,
                                SourcePreviewService sourcePreviewService, SourceService sourceService,
                                SourceDocumentRepository sourceDocumentRepository, DailyLearningAiService aiService,
                                QuizGenerationService quizGenerationService, LearningService learningService,
                                LearningQuizRepository learningQuizRepository, ObjectMapper objectMapper) {
        this.repository = repository; this.articleFeedService = articleFeedService; this.sourcePreviewService = sourcePreviewService;
        this.sourceService = sourceService; this.sourceDocumentRepository = sourceDocumentRepository; this.aiService = aiService;
        this.quizGenerationService = quizGenerationService; this.learningService = learningService;
        this.learningQuizRepository = learningQuizRepository; this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DailyLearningResponse today() { return repository.findByLearningDate(LocalDate.now()).map(this::response).orElse(null); }

    @Transactional(readOnly = true)
    public DailyLearningResponse get(Long id) { return response(find(id)); }

    /** 같은 날짜에는 기존 결과를 돌려주므로 스케줄러·버튼 재시도가 추가 비용을 만들지 않는다. */
    public DailyLearningResponse generateToday(String explicitUrl) {
        DailyLearning existing = repository.findByLearningDate(LocalDate.now()).orElse(null);
        if (existing != null) return response(existing);
        DailyArticleFeedService.ArticleCandidate candidate = explicitUrl == null || explicitUrl.isBlank()
                ? articleFeedService.latest() : new DailyArticleFeedService.ArticleCandidate("데일리 기술 학습 기사", explicitUrl.trim(), null);
        SourcePreviewResponse preview = sourcePreviewService.previewUrl(candidate.url());
        DailyLearning duplicateArticle = repository.findByArticleUrl(preview.sourceUri()).orElse(null);
        if (duplicateArticle != null) return response(duplicateArticle);

        DailyLearningAiDraft draft = aiService.generate(preview.title(), preview.content());
        SourceCreateResponse source = sourceService.create(new SourceCreateRequest(
                preview.title(), preview.content() + "\n\n[AI 보충 해설]\n" + draft.supplement(), SourceType.URL,
                preview.sourceUri(), null, preview.mimeType()));
        SourceDocument document = sourceDocumentRepository.findById(source.sourceId())
                .orElseThrow(() -> new IllegalStateException("데일리 학습 자료 저장을 확인하지 못했습니다."));
        DailyLearning daily = repository.save(new DailyLearning(LocalDate.now(), preview.title(), preview.sourceUri(),
                candidate.publishedAt(), draft.articleSummary(), draft.supplement(), writeConcepts(draft.concepts()),
                draft.reviewTopic().trim(), draft.recommendedReviewQuestionCount(), document));
        return response(daily);
    }

    public QuizViewResponse createReviewQuiz(Long id) { return createQuiz(find(id), DailyLearningTrack.REVIEW, null, 0); }

    public QuizViewResponse createAdvancedQuiz(Long id, String topic, int numberOfQuestions) {
        return createQuiz(find(id), DailyLearningTrack.ADVANCED, topic, numberOfQuestions);
    }

    private QuizViewResponse createQuiz(DailyLearning daily, DailyLearningTrack track, String requestedTopic, int requestedCount) {
        if (track == DailyLearningTrack.REVIEW) {
            LearningQuiz existing = learningQuizRepository.findFirstByDailyLearningIdAndDailyLearningTrackOrderByIdDesc(daily.getId(), track).orElse(null);
            if (existing != null) return learningService.viewQuiz(existing.getId());
        }
        String topic = track == DailyLearningTrack.REVIEW ? daily.getReviewTopic() : requestedTopic.trim();
        int count = track == DailyLearningTrack.REVIEW ? daily.getRecommendedReviewQuestionCount() : requestedCount;
        var created = quizGenerationService.createQuiz(new QuizRequest(topic, count, daily.getSourceDocument().getId(), null, null, false));
        learningService.linkDailyLearning(created.quizId(), daily.getId(), track);
        return QuizViewResponse.from(created);
    }

    private DailyLearning find(Long id) { return repository.findDetailedById(id).orElseThrow(() -> new java.util.NoSuchElementException("데일리 학습을 찾을 수 없습니다.")); }
    private DailyLearningResponse response(DailyLearning daily) {
        Long reviewQuizId = learningQuizRepository.findFirstByDailyLearningIdAndDailyLearningTrackOrderByIdDesc(daily.getId(), DailyLearningTrack.REVIEW)
                .map(LearningQuiz::getId).orElse(null);
        long advancedCount = learningQuizRepository.countByDailyLearningIdAndDailyLearningTrack(daily.getId(), DailyLearningTrack.ADVANCED);
        return new DailyLearningResponse(daily.getId(), daily.getLearningDate(), daily.getArticleTitle(), daily.getArticleUrl(),
                daily.getArticlePublishedAt(), daily.getArticleSummary(), daily.getSupplement(), readConcepts(daily.getConcepts()),
                daily.getReviewTopic(), daily.getRecommendedReviewQuestionCount(), daily.getStatus(), reviewQuizId, advancedCount, daily.getCompletedAt());
    }
    private String writeConcepts(List<DailyLearningConcept> concepts) { try { return objectMapper.writeValueAsString(concepts); } catch (JsonProcessingException e) { throw new IllegalStateException("핵심 개념을 저장하지 못했습니다.", e); } }
    private List<DailyLearningConcept> readConcepts(String value) { try { return objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(List.class, DailyLearningConcept.class)); } catch (JsonProcessingException e) { throw new IllegalStateException("저장된 핵심 개념을 읽지 못했습니다.", e); } }
}
