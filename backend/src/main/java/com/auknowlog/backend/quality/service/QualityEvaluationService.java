package com.auknowlog.backend.quality.service;

import com.auknowlog.backend.quality.dto.QualityEvaluationSummary;
import com.auknowlog.backend.quality.dto.QualityEvaluationSummary.ObjectiveSummary;
import com.auknowlog.backend.quality.dto.QualityEvaluationSummary.ThresholdMetric;
import com.auknowlog.backend.quality.dto.QualityReviewQueue;
import com.auknowlog.backend.quality.dto.QualityRoadmapStepOption;
import com.auknowlog.backend.quality.dto.QualityRunResponse;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository.DuplicateCandidate;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository.LabeledDuplicateSample;
import com.auknowlog.backend.quality.repository.QualityEvaluationRepository.ObjectiveOutcome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Service
public class QualityEvaluationService {

    private static final String DUPLICATE_EVALUATION = "DUPLICATE_THRESHOLD";
    private static final Set<String> DUPLICATE_HUMAN_VERDICTS =
            Set.of("DUPLICATE", "RELATED", "DISTINCT", "SKIPPED");
    private static final Set<String> OBJECTIVE_HUMAN_VERDICTS =
            Set.of("COVERED", "PARTIAL", "MISSING", "ALIGNED", "MISALIGNED", "SKIPPED");
    private static final int RECOMMENDATION_MINIMUM_SAMPLES = 30;

    private final QualityEvaluationRepository repository;
    private final OpenAiQualityEvaluationService openAiQualityEvaluationService;

    @Value("${auknowlog.quality.duplicate.candidate-floor:0.75}")
    private double candidateFloor;

    @Value("${auknowlog.quality.duplicate.automatic-distinct-ceiling:0.80}")
    private double automaticDistinctCeiling;

    @Value("${auknowlog.quality.duplicate.automatic-duplicate-floor:0.95}")
    private double automaticDuplicateFloor;

    public QualityEvaluationService(QualityEvaluationRepository repository,
                                    OpenAiQualityEvaluationService openAiQualityEvaluationService) {
        this.repository = repository;
        this.openAiQualityEvaluationService = openAiQualityEvaluationService;
    }

    @Transactional
    public QualityRunResponse evaluateDuplicateThreshold(int requestedMaxPairs) {
        int maxPairs = Math.max(10, Math.min(requestedMaxPairs, 500));
        long runId = repository.createRun(
                DUPLICATE_EVALUATION,
                "question_history",
                "pgvector-cosine",
                "duplicate-threshold-v1"
        );

        List<DuplicateCandidate> candidates = repository.findDuplicateCandidates(candidateFloor, maxPairs);
        int reviewRequired = 0;
        for (DuplicateCandidate candidate : candidates) {
            String verdict = systemDuplicateVerdict(candidate.similarity());
            if ("REVIEW_REQUIRED".equals(verdict)) {
                reviewRequired++;
            }
            long pairId = repository.upsertDuplicatePair(candidate.questionAId(), candidate.questionBId());
            repository.insertDuplicateResult(
                    runId, pairId, candidate.similarity(), candidate.embeddingModel(), verdict);
        }
        repository.completeRun(runId, candidates.size(), reviewRequired, null, null, 0L, null);
        return new QualityRunResponse(
                runId, DUPLICATE_EVALUATION, "COMPLETED", candidates.size(), reviewRequired, 0L);
    }

    public QualityRunResponse evaluateObjectiveQuality(long roadmapStepId) {
        return openAiQualityEvaluationService.evaluate(roadmapStepId);
    }

    @Transactional
    public void reviewDuplicate(long pairId, String verdict) {
        String normalized = normalizedVerdict(verdict, DUPLICATE_HUMAN_VERDICTS);
        if (repository.reviewDuplicatePair(pairId, normalized) == 0) {
            throw new IllegalArgumentException("검토할 중복 문제 쌍을 찾을 수 없습니다.");
        }
    }

    @Transactional
    public void reviewObjective(long caseId, String verdict) {
        String normalized = normalizedVerdict(verdict, OBJECTIVE_HUMAN_VERDICTS);
        if (repository.reviewObjectiveCase(caseId, normalized) == 0) {
            throw new IllegalArgumentException("검토할 목표 품질 항목을 찾을 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public QualityEvaluationSummary summary() {
        var duplicateCounts = repository.duplicateCounts();
        List<ThresholdMetric> thresholdMetrics = thresholdMetrics(repository.findLatestLabeledDuplicateSamples());
        Double recommendedThreshold = thresholdMetrics.stream()
                .filter(metric -> metric.sampleSize() >= RECOMMENDATION_MINIMUM_SAMPLES)
                .filter(metric -> metric.precisionPercent() != null && metric.precisionPercent() >= 90)
                .max(Comparator.comparingDouble((ThresholdMetric metric) -> value(metric.recallPercent()))
                        .thenComparingDouble(metric -> value(metric.f1Percent()))
                        .thenComparingDouble(metric -> -metric.threshold()))
                .map(ThresholdMetric::threshold)
                .orElse(null);

        var objectiveCounts = repository.objectiveCounts();
        List<ObjectiveOutcome> outcomes = repository.objectiveOutcomes();
        ObjectiveSummary objectiveSummary = new ObjectiveSummary(
                objectiveCounts.total(),
                objectiveCounts.automaticAccepted(),
                objectiveCounts.pending(),
                objectiveCounts.reviewed(),
                omissionRate(outcomes, false),
                alignmentRate(outcomes, false),
                omissionRate(outcomes, true),
                alignmentRate(outcomes, true)
        );

        return new QualityEvaluationSummary(
                new QualityEvaluationSummary.DuplicateSummary(
                        duplicateCounts.total(), duplicateCounts.reviewed(), duplicateCounts.pending(),
                        candidateFloor, automaticDistinctCeiling, automaticDuplicateFloor,
                        recommendedThreshold, thresholdMetrics
                ),
                objectiveSummary,
                repository.recentRuns()
        );
    }

    @Transactional(readOnly = true)
    public QualityReviewQueue reviewQueue(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return new QualityReviewQueue(
                repository.findDuplicateReviewQueue(limit),
                repository.findObjectiveReviewQueue(limit)
        );
    }

    @Transactional(readOnly = true)
    public List<QualityRoadmapStepOption> roadmapSteps() {
        return repository.findRoadmapStepOptions();
    }

    private String systemDuplicateVerdict(double similarity) {
        if (similarity >= automaticDuplicateFloor) {
            return "DUPLICATE";
        }
        if (similarity < automaticDistinctCeiling) {
            return "DISTINCT";
        }
        return "REVIEW_REQUIRED";
    }

    private String normalizedVerdict(String verdict, Set<String> allowed) {
        String normalized = verdict == null ? "" : verdict.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("지원하지 않는 품질 검토 판정입니다.");
        }
        return normalized;
    }

    private List<ThresholdMetric> thresholdMetrics(List<LabeledDuplicateSample> samples) {
        return IntStream.rangeClosed(75, 95)
                .mapToObj(value -> thresholdMetric(value / 100.0, samples))
                .toList();
    }

    private ThresholdMetric thresholdMetric(double threshold, List<LabeledDuplicateSample> samples) {
        long truePositive = 0;
        long falsePositive = 0;
        long falseNegative = 0;
        long trueNegative = 0;
        for (LabeledDuplicateSample sample : samples) {
            boolean actualDuplicate = "DUPLICATE".equals(sample.humanVerdict());
            boolean predictedDuplicate = sample.similarity() >= threshold;
            if (actualDuplicate && predictedDuplicate) truePositive++;
            if (!actualDuplicate && predictedDuplicate) falsePositive++;
            if (actualDuplicate && !predictedDuplicate) falseNegative++;
            if (!actualDuplicate && !predictedDuplicate) trueNegative++;
        }
        Double precision = ratio(truePositive, truePositive + falsePositive);
        Double recall = ratio(truePositive, truePositive + falseNegative);
        Double f1 = precision == null || recall == null || precision + recall == 0
                ? null : roundOneDecimal(2 * precision * recall / (precision + recall));
        return new ThresholdMetric(
                threshold, samples.size(), truePositive, falsePositive, falseNegative, trueNegative,
                precision, recall, f1
        );
    }

    private Integer omissionRate(List<ObjectiveOutcome> outcomes, boolean humanOnly) {
        List<String> verdicts = outcomes.stream()
                .filter(outcome -> "OBJECTIVE_COVERAGE".equals(outcome.caseType()))
                .filter(outcome -> !humanOnly || outcome.humanVerdict() != null)
                .map(outcome -> effectiveVerdict(outcome, humanOnly))
                .filter(verdict -> verdict != null && !"SKIPPED".equals(verdict))
                .toList();
        if (verdicts.isEmpty()) return null;
        long missing = verdicts.stream().filter("MISSING"::equals).count();
        return percentage(missing, verdicts.size());
    }

    private Integer alignmentRate(List<ObjectiveOutcome> outcomes, boolean humanOnly) {
        List<String> verdicts = outcomes.stream()
                .filter(outcome -> "QUESTION_ALIGNMENT".equals(outcome.caseType()))
                .filter(outcome -> !humanOnly || outcome.humanVerdict() != null)
                .map(outcome -> effectiveVerdict(outcome, humanOnly))
                .filter(verdict -> verdict != null && !"SKIPPED".equals(verdict))
                .toList();
        if (verdicts.isEmpty()) return null;
        long aligned = verdicts.stream().filter("ALIGNED"::equals).count();
        return percentage(aligned, verdicts.size());
    }

    private String effectiveVerdict(ObjectiveOutcome outcome, boolean humanOnly) {
        if (outcome.humanVerdict() != null) return outcome.humanVerdict();
        return humanOnly ? null : outcome.aiVerdict();
    }

    private Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : roundOneDecimal((double) numerator * 100 / denominator);
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private int percentage(long numerator, long denominator) {
        return denominator == 0 ? 0 : (int) Math.round((double) numerator * 100 / denominator);
    }

    private double value(Double value) {
        return value == null ? -1 : value;
    }
}
