package com.auknowlog.backend.dashboard.service;

import com.auknowlog.backend.ai.dto.AiBudgetSnapshot;
import com.auknowlog.backend.ai.repository.AiGenerationLogRepository;
import com.auknowlog.backend.ai.service.AiUsagePolicyService;
import com.auknowlog.backend.dashboard.dto.DashboardSummary;
import com.auknowlog.backend.feedback.repository.QuestionFeedbackRepository;
import com.auknowlog.backend.learning.repository.LearningAttemptRepository;
import com.auknowlog.backend.learning.repository.ReviewScheduleRepository;
import com.auknowlog.backend.quality.dto.QualityEvaluationSummary;
import com.auknowlog.backend.quality.service.QualityEvaluationService;
import com.auknowlog.backend.question.repository.QuestionHistoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    void exposesHumanEvidenceAndProvisionalQualityMetricsAsSeparateDashboardData() {
        LearningAttemptRepository attempts = mock(LearningAttemptRepository.class);
        ReviewScheduleRepository reviews = mock(ReviewScheduleRepository.class);
        AiGenerationLogRepository aiLogs = mock(AiGenerationLogRepository.class);
        AiUsagePolicyService aiUsagePolicy = mock(AiUsagePolicyService.class);
        QuestionHistoryRepository questions = mock(QuestionHistoryRepository.class);
        QuestionFeedbackRepository feedback = mock(QuestionFeedbackRepository.class);
        QualityEvaluationService qualityEvaluation = mock(QualityEvaluationService.class);

        when(attempts.findAllWithQuiz()).thenReturn(List.of());
        when(reviews.countByStatusAndNextReviewAtLessThanEqual(any(), any())).thenReturn(0L);
        when(aiLogs.findByCreatedAtGreaterThanEqual(any())).thenReturn(List.of());
        when(aiUsagePolicy.snapshot()).thenReturn(new AiBudgetSnapshot(true, 50_000, 0, 50_000, 0));
        when(questions.count()).thenReturn(0L);
        when(feedback.findAll()).thenReturn(List.of());
        when(feedback.count()).thenReturn(0L);
        when(feedback.countByStatus("OPEN")).thenReturn(0L);
        when(qualityEvaluation.summary()).thenReturn(qualitySummary());

        DashboardSummary dashboard = new DashboardService(
                attempts, reviews, aiLogs, aiUsagePolicy, questions, feedback, qualityEvaluation
        ).getSummary();

        assertThat(dashboard.qualityEvaluation().duplicateHumanSampleCount()).isEqualTo(3);
        assertThat(dashboard.qualityEvaluation().duplicatePendingReviewCount()).isEqualTo(2);
        assertThat(dashboard.qualityEvaluation().duplicateThresholdMetrics()).hasSize(2);
        assertThat(dashboard.qualityEvaluation().objectiveQuality().provisionalOmissionRatePercent()).isEqualTo(25);
        assertThat(dashboard.qualityEvaluation().objectiveQuality().humanVerifiedOmissionRatePercent()).isEqualTo(50);
        assertThat(dashboard.qualityEvaluation().objectiveQuality().provisionalAlignmentRatePercent()).isEqualTo(75);
        assertThat(dashboard.qualityEvaluation().objectiveQuality().humanVerifiedAlignmentRatePercent()).isEqualTo(100);
    }

    private QualityEvaluationSummary qualitySummary() {
        return new QualityEvaluationSummary(
                new QualityEvaluationSummary.DuplicateSummary(
                        5, 3, 2, 0.75, 0.80, 0.95, null,
                        List.of(
                                new QualityEvaluationSummary.ThresholdMetric(0.75, 3, 2, 1, 0, 0, 66.7, 100.0, 80.0),
                                new QualityEvaluationSummary.ThresholdMetric(0.76, 3, 2, 0, 0, 1, 100.0, 100.0, 100.0)
                        )
                ),
                new QualityEvaluationSummary.ObjectiveSummary(4, 1, 2, 1, 25, 75, 50, 100),
                List.of()
        );
    }
}
