package com.auknowlog.backend.quality.service;

import com.auknowlog.backend.embedding.service.EmbeddingResult;
import com.auknowlog.backend.embedding.service.EmbeddingService;
import com.auknowlog.backend.quality.repository.DuplicateEvaluationDatasetRepository;
import com.auknowlog.backend.quality.repository.DuplicateEvaluationDatasetRepository.DatasetRow;
import com.auknowlog.backend.quality.repository.DuplicateEvaluationDatasetRepository.DatasetSampleRow;
import com.auknowlog.backend.quality.repository.DuplicateEvaluationDatasetRepository.DatasetSummaryRow;
import com.auknowlog.backend.quality.repository.DuplicateEvaluationDatasetRepository.LabeledSimilaritySample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DuplicateEvaluationDatasetServiceTest {

    private DuplicateEvaluationDatasetRepository repository;
    private EmbeddingService embeddingService;
    private DuplicateEvaluationDatasetService service;

    @BeforeEach
    void setUp() {
        repository = mock(DuplicateEvaluationDatasetRepository.class);
        embeddingService = mock(EmbeddingService.class);
        service = new DuplicateEvaluationDatasetService(repository, embeddingService);
    }

    @Test
    void createsFortyTwoCuratedSamplesWithoutCallingOpenAi() {
        when(repository.findByKey("backend-korean-v1")).thenReturn(Optional.empty());
        when(repository.createDataset("backend-korean-v1", "백엔드 핵심 개념 중복 평가 표본", "1.0", 42))
                .thenReturn(7L);
        when(repository.findAllSummaries()).thenReturn(List.of(summary("DRAFT", 42, 0)));
        when(repository.findEmbeddedSamples(7L)).thenReturn(List.of());

        var response = service.createStandardDataset();

        assertThat(response.totalSamples()).isEqualTo(42);
        assertThat(response.duplicateSamples()).isEqualTo(14);
        assertThat(response.relatedSamples()).isEqualTo(14);
        assertThat(response.distinctSamples()).isEqualTo(14);
        verify(repository, times(42)).insertSample(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.anyInt(), anyString(),
                anyString(), anyString(), anyString(), anyString());
        verify(embeddingService, times(0)).embed(anyString());
    }

    @Test
    void embedsOnlyPendingSamplesAndCalculatesReferenceMetric() {
        DatasetRow draft = new DatasetRow(7L, "backend-korean-v1", "백엔드 핵심 개념 중복 평가 표본",
                "1.0", "CURATED_REFERENCE", "DRAFT", null, 0, LocalDateTime.now(), null);
        when(repository.findById(7L)).thenReturn(Optional.of(draft));
        when(repository.findPendingEmbeddings(7L)).thenReturn(List.of(
                new DatasetSampleRow(11L, 1, "Kubernetes", "Pod의 역할은?", "Pod가 하는 일은?", "DUPLICATE", "표현만 변경"),
                new DatasetSampleRow(12L, 2, "Kubernetes", "Pod의 역할은?", "Service의 역할은?", "DISTINCT", "다른 목표")
        ));
        when(embeddingService.embed(anyString())).thenAnswer(invocation ->
                Optional.of(new EmbeddingResult("fixture-embedding", vector(1, 0), 5)));
        when(repository.findAllSummaries()).thenReturn(List.of(summary("READY", 42, 2)));
        when(repository.findEmbeddedSamples(7L)).thenReturn(List.of(
                new LabeledSimilaritySample(11L, "DUPLICATE", 0.99),
                new LabeledSimilaritySample(12L, "DISTINCT", 0.70)
        ));

        var response = service.embedDataset(7L);

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.embeddingInputTokens()).isEqualTo(15);
        assertThat(response.thresholdMetrics()).filteredOn(metric -> metric.threshold() == 0.90)
                .singleElement()
                .satisfies(metric -> {
                    assertThat(metric.precisionPercent()).isEqualTo(100.0);
                    assertThat(metric.recallPercent()).isEqualTo(100.0);
                });
        verify(repository).saveEmbedding(org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(repository).saveEmbedding(org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(repository).markReady(7L, "fixture-embedding", 15L);
    }

    private DatasetSummaryRow summary(String status, int total, int embedded) {
        return new DatasetSummaryRow(7L, "backend-korean-v1", "백엔드 핵심 개념 중복 평가 표본",
                "1.0", "CURATED_REFERENCE", status, embedded == 0 ? null : "fixture-embedding", 15,
                total, embedded, 14, 14, 14);
    }

    private float[] vector(float first, float second) {
        float[] values = new float[512];
        values[0] = first;
        values[1] = second;
        return values;
    }
}
