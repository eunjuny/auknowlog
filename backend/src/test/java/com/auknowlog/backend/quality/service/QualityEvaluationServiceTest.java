package com.auknowlog.backend.quality.service;

import com.auknowlog.backend.quality.dto.QualityEvaluationSummary;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository.DuplicateCandidate;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository.DuplicateCounts;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository.LabeledDuplicateSample;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository.ObjectiveCounts;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository.ObjectiveOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityEvaluationServiceTest {

    private QualityEvaluationRepository repository;
    private QualityEvaluationService service;

    @BeforeEach
    void setUp() {
        repository = mock(QualityEvaluationRepository.class);
        service = new QualityEvaluationService(repository, mock(OpenAiQualityEvaluationService.class));
        ReflectionTestUtils.setField(service, "candidateFloor", 0.75);
        ReflectionTestUtils.setField(service, "automaticDistinctCeiling", 0.80);
        ReflectionTestUtils.setField(service, "automaticDuplicateFloor", 0.95);
    }

    @Test
    void collectsCandidatesWithoutAiAndOnlyQueuesTheAmbiguousBand() {
        when(repository.createRun("DUPLICATE_THRESHOLD", "question_history",
                "pgvector-cosine", "duplicate-threshold-v1")).thenReturn(7L);
        when(repository.findDuplicateCandidates(anyDouble(), anyInt())).thenReturn(List.of(
                new DuplicateCandidate(1, "Kubernetes", "Pod란?", 2, "Kubernetes", "Pod 역할은?", 0.87, "embedding-model"),
                new DuplicateCandidate(3, "Java", "JVM은?", 4, "Java", "JVM의 역할은?", 0.97, "embedding-model")
        ));
        when(repository.upsertDuplicatePair(1, 2)).thenReturn(11L);
        when(repository.upsertDuplicatePair(3, 4)).thenReturn(12L);

        var result = service.evaluateDuplicateThreshold(100);

        assertThat(result.totalTokens()).isZero();
        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.reviewRequiredCount()).isEqualTo(1);
        verify(repository).insertDuplicateResult(7, 11, 0.87, "embedding-model", "REVIEW_REQUIRED");
        verify(repository).insertDuplicateResult(7, 12, 0.97, "embedding-model", "DUPLICATE");
        verify(repository).completeRun(7, 2, 1, null, null, 0L, null);
    }

    @Test
    void calculatesThresholdEvidenceOnlyFromHumanLabeledPairs() {
        when(repository.duplicateCounts()).thenReturn(new DuplicateCounts(3, 3, 0));
        when(repository.findLatestLabeledDuplicateSamples()).thenReturn(List.of(
                new LabeledDuplicateSample(1, "DUPLICATE", 0.92),
                new LabeledDuplicateSample(2, "DUPLICATE", 0.84),
                new LabeledDuplicateSample(3, "DISTINCT", 0.88)
        ));
        when(repository.objectiveCounts()).thenReturn(new ObjectiveCounts(4, 2, 0, 2));
        when(repository.objectiveOutcomes()).thenReturn(List.of(
                new ObjectiveOutcome("OBJECTIVE_COVERAGE", "MISSING", "MISSING"),
                new ObjectiveOutcome("OBJECTIVE_COVERAGE", "COVERED", null),
                new ObjectiveOutcome("QUESTION_ALIGNMENT", "ALIGNED", "ALIGNED"),
                new ObjectiveOutcome("QUESTION_ALIGNMENT", "MISALIGNED", null)
        ));
        when(repository.recentRuns()).thenReturn(List.of());

        QualityEvaluationSummary summary = service.summary();
        var atNinety = summary.duplicate().thresholdMetrics().stream()
                .filter(metric -> metric.threshold() == 0.90)
                .findFirst().orElseThrow();

        assertThat(atNinety.sampleSize()).isEqualTo(3);
        assertThat(atNinety.truePositive()).isEqualTo(1);
        assertThat(atNinety.falsePositive()).isZero();
        assertThat(atNinety.falseNegative()).isEqualTo(1);
        assertThat(atNinety.trueNegative()).isEqualTo(1);
        assertThat(atNinety.precisionPercent()).isEqualTo(100.0);
        assertThat(atNinety.recallPercent()).isEqualTo(50.0);
        assertThat(summary.duplicate().recommendedThreshold()).isNull();
        assertThat(summary.objective().provisionalOmissionRatePercent()).isEqualTo(50);
        assertThat(summary.objective().provisionalAlignmentRatePercent()).isEqualTo(50);
        assertThat(summary.objective().humanVerifiedOmissionRatePercent()).isEqualTo(100);
        assertThat(summary.objective().humanVerifiedAlignmentRatePercent()).isEqualTo(100);
    }
}
