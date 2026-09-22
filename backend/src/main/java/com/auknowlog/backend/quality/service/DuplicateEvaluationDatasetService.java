package com.auknowlog.backend.quality.service;

import com.auknowlog.backend.embedding.service.EmbeddingResult;
import com.auknowlog.backend.embedding.service.EmbeddingService;
import com.auknowlog.backend.quality.dto.DuplicateDatasetResponse;
import com.auknowlog.backend.quality.dto.QualityEvaluationSummary.ThresholdMetric;
import com.auknowlog.backend.quality.repository.DuplicateEvaluationDatasetRepository;
import com.auknowlog.backend.quality.repository.DuplicateEvaluationDatasetRepository.DatasetSampleRow;
import com.auknowlog.backend.quality.repository.DuplicateEvaluationDatasetRepository.LabeledSimilaritySample;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

@Service
public class DuplicateEvaluationDatasetService {

    private static final String STANDARD_DATASET_KEY = "backend-korean-v1";
    private static final String STANDARD_DATASET_TITLE = "백엔드 핵심 개념 중복 평가 표본";
    private static final String STANDARD_DATASET_VERSION = "1.0";

    private final DuplicateEvaluationDatasetRepository repository;
    private final EmbeddingService embeddingService;

    public DuplicateEvaluationDatasetService(DuplicateEvaluationDatasetRepository repository,
                                             EmbeddingService embeddingService) {
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public DuplicateDatasetResponse createStandardDataset() {
        var existing = repository.findByKey(STANDARD_DATASET_KEY);
        if (existing.isPresent()) {
            return response(existing.get().id());
        }

        long datasetId = repository.createDataset(
                STANDARD_DATASET_KEY, STANDARD_DATASET_TITLE, STANDARD_DATASET_VERSION, standardSamples().size());
        int order = 1;
        for (SampleDefinition sample : standardSamples()) {
            repository.insertSample(datasetId, order++, sample.topic(), sample.questionA(), sample.questionB(),
                    sample.referenceVerdict(), sample.referenceRationale());
        }
        return response(datasetId);
    }

    @Transactional(readOnly = true)
    public List<DuplicateDatasetResponse> listDatasets() {
        return repository.findAllSummaries().stream().map(summary -> response(summary.id())).toList();
    }

    public DuplicateDatasetResponse embedDataset(long datasetId) {
        var dataset = repository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("중복 평가 데이터셋을 찾을 수 없습니다."));
        List<DatasetSampleRow> pendingSamples = repository.findPendingEmbeddings(datasetId);
        if (pendingSamples.isEmpty()) {
            return response(datasetId);
        }

        repository.markProcessing(datasetId);
        try {
            Map<String, EmbeddingResult> embeddings = new LinkedHashMap<>();
            long inputTokens = 0;
            for (DatasetSampleRow sample : pendingSamples) {
                inputTokens += embedOnce(sample.questionA(), embeddings).inputTokens();
                inputTokens += embedOnce(sample.questionB(), embeddings).inputTokens();
            }
            for (DatasetSampleRow sample : pendingSamples) {
                repository.saveEmbedding(sample.id(), embeddings.get(sample.questionA()), embeddings.get(sample.questionB()));
            }
            String embeddingModel = embeddings.values().stream().findFirst()
                    .map(EmbeddingResult::model)
                    .orElse(dataset.embeddingModel());
            repository.markReady(datasetId, embeddingModel, dataset.embeddingInputTokens() + inputTokens);
            return response(datasetId);
        } catch (RuntimeException exception) {
            repository.markFailed(datasetId);
            throw exception;
        }
    }

    @Transactional
    public void reviewSample(long datasetId, int sampleOrder, String verdict) {
        String normalized = verdict == null ? "" : verdict.trim().toUpperCase();
        if (!Set.of("DUPLICATE", "RELATED", "DISTINCT", "SKIPPED").contains(normalized)) {
            throw new IllegalArgumentException("지원하지 않는 기준 표본 판정입니다.");
        }
        if (repository.reviewSample(datasetId, sampleOrder, normalized) == 0) {
            throw new IllegalArgumentException("검토할 기준 표본을 찾을 수 없습니다.");
        }
    }

    private EmbeddingResult embedOnce(String text, Map<String, EmbeddingResult> embeddings) {
        EmbeddingResult cached = embeddings.get(text);
        if (cached != null) {
            return new EmbeddingResult(cached.model(), cached.values(), 0);
        }
        Optional<EmbeddingResult> result = embeddingService.embed(text);
        EmbeddingResult embedding = result.orElseThrow(() ->
                new IllegalStateException("임베딩이 비활성화되어 데이터셋 유사도를 계산할 수 없습니다."));
        embeddings.put(text, embedding);
        return embedding;
    }

    private DuplicateDatasetResponse response(long datasetId) {
        var summary = repository.findAllSummaries().stream()
                .filter(value -> value.id() == datasetId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("중복 평가 데이터셋을 찾을 수 없습니다."));
        return new DuplicateDatasetResponse(
                summary.id(), summary.datasetKey(), summary.title(), summary.datasetVersion(), summary.sourceType(),
                summary.status(), summary.totalSamples(), summary.embeddedSamples(), summary.duplicateSamples(),
                summary.relatedSamples(), summary.distinctSamples(), summary.embeddingInputTokens(), summary.embeddingModel(),
                thresholdMetrics(repository.findEmbeddedSamples(datasetId))
        );
    }

    private List<ThresholdMetric> thresholdMetrics(List<LabeledSimilaritySample> samples) {
        return IntStream.rangeClosed(75, 95)
                .mapToObj(value -> thresholdMetric(value / 100.0, samples))
                .toList();
    }

    private ThresholdMetric thresholdMetric(double threshold, List<LabeledSimilaritySample> samples) {
        long truePositive = 0;
        long falsePositive = 0;
        long falseNegative = 0;
        long trueNegative = 0;
        for (LabeledSimilaritySample sample : samples) {
            boolean actualDuplicate = "DUPLICATE".equals(sample.referenceVerdict());
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
        return new ThresholdMetric(threshold, samples.size(), truePositive, falsePositive, falseNegative, trueNegative,
                precision, recall, f1);
    }

    private Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : roundOneDecimal((double) numerator * 100 / denominator);
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private List<SampleDefinition> standardSamples() {
        return List.of(
                sample("Kubernetes Pod", "Kubernetes에서 컨테이너를 실행하는 가장 작은 배포 단위는 무엇인가요?",
                        "하나 이상의 컨테이너를 함께 실행하는 Kubernetes의 최소 단위는 무엇인가요?", "DUPLICATE", "동일한 Pod 정의를 표현만 바꿨습니다."),
                sample("Kubernetes Pod", "Kubernetes에서 컨테이너를 실행하는 가장 작은 배포 단위는 무엇인가요?",
                        "Pod가 재시작되는 대표적인 상황은 무엇인가요?", "RELATED", "같은 Pod 주제지만 개념 정의와 장애 동작은 다릅니다."),
                sample("Kubernetes Pod", "Kubernetes에서 컨테이너를 실행하는 가장 작은 배포 단위는 무엇인가요?",
                        "Kubernetes Service가 Pod 집합에 제공하는 핵심 기능은 무엇인가요?", "DISTINCT", "Pod 정의와 Service 네트워크 역할은 다른 학습 목표입니다."),

                sample("Kubernetes Deployment", "Kubernetes Deployment가 관리하는 주요 리소스는 무엇인가요?",
                        "ReplicaSet을 통해 Pod의 원하는 개수를 유지하도록 선언하는 Kubernetes 객체는 무엇인가요?", "DUPLICATE", "둘 다 Deployment의 선언적 Pod 관리 역할을 묻습니다."),
                sample("Kubernetes Deployment", "Kubernetes Deployment가 관리하는 주요 리소스는 무엇인가요?",
                        "롤링 업데이트 중 새 ReplicaSet이 준비되지 않으면 Deployment는 어떻게 동작하나요?", "RELATED", "같은 리소스지만 기본 역할과 업데이트 실패 동작은 다릅니다."),
                sample("Kubernetes Deployment", "Kubernetes Deployment가 관리하는 주요 리소스는 무엇인가요?",
                        "ConfigMap에는 어떤 종류의 설정 값을 저장하나요?", "DISTINCT", "Deployment와 ConfigMap은 다른 리소스 역할을 묻습니다."),

                sample("메시징 멱등성", "같은 메시지를 여러 번 처리해도 최종 결과가 같도록 만드는 특성은 무엇인가요?",
                        "반복된 메시지 요청에도 최종 상태가 변하지 않는 성질을 무엇이라고 하나요?", "DUPLICATE", "동일한 멱등성 개념을 다른 문장으로 표현했습니다."),
                sample("메시징 멱등성", "같은 메시지를 여러 번 처리해도 최종 결과가 같도록 만드는 특성은 무엇인가요?",
                        "메시지 소비자가 중복 처리를 막기 위해 활용할 수 있는 저장 전략은 무엇인가요?", "RELATED", "멱등성 구현 전략은 개념 정의와 다른 판단을 요구합니다."),
                sample("메시징 멱등성", "같은 메시지를 여러 번 처리해도 최종 결과가 같도록 만드는 특성은 무엇인가요?",
                        "메시지 브로커에서 순서 보장을 위해 사용하는 단위는 무엇인가요?", "DISTINCT", "멱등성과 순서 보장은 별개인 신뢰성 특성입니다."),

                sample("트랜잭션 격리", "READ COMMITTED에서 재조회하면 다른 트랜잭션이 커밋한 변경을 볼 수 있나요?",
                        "READ COMMITTED 격리 수준에서는 이미 커밋된 다른 트랜잭션의 값을 다시 읽을 때 반영될 수 있나요?", "DUPLICATE", "동일한 격리 수준의 재조회 가시성을 묻습니다."),
                sample("트랜잭션 격리", "READ COMMITTED에서 재조회하면 다른 트랜잭션이 커밋한 변경을 볼 수 있나요?",
                        "SERIALIZABLE 격리 수준은 READ COMMITTED보다 어떤 동시성 제약을 추가하나요?", "RELATED", "둘 다 격리 수준이지만 비교와 가시성은 다른 목표입니다."),
                sample("트랜잭션 격리", "READ COMMITTED에서 재조회하면 다른 트랜잭션이 커밋한 변경을 볼 수 있나요?",
                        "데이터베이스 인덱스가 조회 성능에 주는 영향은 무엇인가요?", "DISTINCT", "트랜잭션 격리와 인덱스 성능은 다른 주제입니다."),

                sample("Redis 캐시", "Redis를 캐시로 사용할 때 TTL의 주된 목적은 무엇인가요?",
                        "캐시 항목에 만료 시간을 두는 이유는 무엇인가요?", "DUPLICATE", "둘 다 TTL로 오래된 캐시를 제한하는 목적을 묻습니다."),
                sample("Redis 캐시", "Redis를 캐시로 사용할 때 TTL의 주된 목적은 무엇인가요?",
                        "캐시 스탬피드를 줄이기 위한 대표적인 방법은 무엇인가요?", "RELATED", "캐시 운영이지만 만료 정책과 동시 갱신 제어는 다릅니다."),
                sample("Redis 캐시", "Redis를 캐시로 사용할 때 TTL의 주된 목적은 무엇인가요?",
                        "Redis Pub/Sub에서 구독자가 오프라인이면 메시지는 보장되나요?", "DISTINCT", "캐시 만료와 Pub/Sub 전달 보장은 다른 기능입니다."),

                sample("Kafka Consumer Group", "같은 Kafka consumer group에서 하나의 파티션은 동시에 몇 consumer가 처리할 수 있나요?",
                        "동일 consumer group 안에서 한 파티션을 병렬로 처리하는 consumer 수의 최대값은 얼마인가요?", "DUPLICATE", "동일 그룹의 파티션 독점 소비 원리를 묻습니다."),
                sample("Kafka Consumer Group", "같은 Kafka consumer group에서 하나의 파티션은 동시에 몇 consumer가 처리할 수 있나요?",
                        "파티션 수가 consumer 수보다 적으면 어떤 일이 발생하나요?", "RELATED", "같은 할당 원리지만 유휴 consumer 판단을 묻습니다."),
                sample("Kafka Consumer Group", "같은 Kafka consumer group에서 하나의 파티션은 동시에 몇 consumer가 처리할 수 있나요?",
                        "Kafka producer의 acks 설정은 무엇을 제어하나요?", "DISTINCT", "consumer 할당과 producer 확인 정책은 다른 영역입니다."),

                sample("HTTP 멱등성", "같은 HTTP PUT 요청을 여러 번 보내도 최종 리소스 상태가 같아야 한다는 성질은 무엇인가요?",
                        "반복한 PUT 요청이 동일한 최종 상태를 만들도록 기대하는 HTTP 특성은 무엇인가요?", "DUPLICATE", "동일한 PUT 멱등성을 묻습니다."),
                sample("HTTP 멱등성", "같은 HTTP PUT 요청을 여러 번 보내도 최종 리소스 상태가 같아야 한다는 성질은 무엇인가요?",
                        "HTTP POST 요청은 일반적으로 멱등성을 보장하나요?", "RELATED", "같은 개념이지만 다른 HTTP 메서드의 성질을 묻습니다."),
                sample("HTTP 멱등성", "같은 HTTP PUT 요청을 여러 번 보내도 최종 리소스 상태가 같아야 한다는 성질은 무엇인가요?",
                        "HTTP 404 응답은 어떤 상태를 의미하나요?", "DISTINCT", "멱등성과 HTTP 상태 코드는 다른 개념입니다."),

                sample("OAuth 2.0", "OAuth 2.0 access token의 주된 용도는 무엇인가요?",
                        "OAuth access token은 resource server의 API 접근에 무엇을 증명하나요?", "DUPLICATE", "둘 다 access token으로 API 접근 권한을 전달하는 역할을 묻습니다."),
                sample("OAuth 2.0", "OAuth 2.0 access token의 주된 용도는 무엇인가요?",
                        "OAuth refresh token은 언제 사용하는 것이 적절한가요?", "RELATED", "같은 인증 흐름이지만 access token과 refresh token의 역할은 다릅니다."),
                sample("OAuth 2.0", "OAuth 2.0 access token의 주된 용도는 무엇인가요?",
                        "CSRF 공격을 줄이기 위해 브라우저 요청에서 확인할 수 있는 값은 무엇인가요?", "DISTINCT", "OAuth 토큰 역할과 CSRF 방어는 다른 보안 목표입니다."),

                sample("Prometheus", "Prometheus가 대상 애플리케이션의 메트릭을 주로 가져오는 방식은 무엇인가요?",
                        "Prometheus 서버가 대상의 metrics endpoint를 주기적으로 조회하는 수집 방식은 무엇인가요?", "DUPLICATE", "동일한 pull 기반 수집 방식을 묻습니다."),
                sample("Prometheus", "Prometheus가 대상 애플리케이션의 메트릭을 주로 가져오는 방식은 무엇인가요?",
                        "Prometheus Counter와 Gauge는 어떤 값의 변화에 각각 적합한가요?", "RELATED", "같은 관측 도구이지만 수집 방식과 메트릭 타입은 다릅니다."),
                sample("Prometheus", "Prometheus가 대상 애플리케이션의 메트릭을 주로 가져오는 방식은 무엇인가요?",
                        "Kibana는 주로 어떤 데이터를 탐색하고 시각화하나요?", "DISTINCT", "Prometheus 수집과 Kibana 로그 탐색은 다른 기능입니다."),

                sample("PostgreSQL 인덱스", "PostgreSQL에서 B-tree 인덱스가 주로 빠르게 만드는 조회 조건은 무엇인가요?",
                        "정렬 가능한 값의 동등 비교와 범위 검색에 기본적으로 사용되는 PostgreSQL 인덱스는 무엇인가요?", "DUPLICATE", "동일한 B-tree의 기본 사용 범위를 묻습니다."),
                sample("PostgreSQL 인덱스", "PostgreSQL에서 B-tree 인덱스가 주로 빠르게 만드는 조회 조건은 무엇인가요?",
                        "인덱스를 많이 만들면 쓰기 작업에는 어떤 비용이 생기나요?", "RELATED", "같은 인덱스 주제지만 조회 적용과 쓰기 비용은 다릅니다."),
                sample("PostgreSQL 인덱스", "PostgreSQL에서 B-tree 인덱스가 주로 빠르게 만드는 조회 조건은 무엇인가요?",
                        "PostgreSQL VACUUM은 주로 어떤 문제를 관리하나요?", "DISTINCT", "인덱스 접근 방식과 MVCC 정리는 다른 학습 목표입니다."),

                sample("Docker", "Docker image와 container의 관계를 가장 잘 설명한 것은 무엇인가요?",
                        "실행 가능한 불변 템플릿인 Docker image에서 실제 실행 인스턴스로 만들어지는 것은 무엇인가요?", "DUPLICATE", "동일한 image와 container의 관계를 묻습니다."),
                sample("Docker", "Docker image와 container의 관계를 가장 잘 설명한 것은 무엇인가요?",
                        "Docker image layer를 재사용하면 빌드 과정에 어떤 이점이 있나요?", "RELATED", "같은 image 주제이지만 실행 모델과 layer 캐시는 다릅니다."),
                sample("Docker", "Docker image와 container의 관계를 가장 잘 설명한 것은 무엇인가요?",
                        "Kubernetes namespace는 무엇을 논리적으로 분리하나요?", "DISTINCT", "컨테이너 실행 모델과 Kubernetes 리소스 범위는 다릅니다."),

                sample("Spring 트랜잭션", "Spring의 @Transactional이 주로 관리하는 경계는 무엇인가요?",
                        "Spring에서 메서드 실행을 하나의 데이터베이스 트랜잭션으로 묶도록 선언하는 애너테이션은 무엇인가요?", "DUPLICATE", "동일한 @Transactional의 트랜잭션 경계를 묻습니다."),
                sample("Spring 트랜잭션", "Spring의 @Transactional이 주로 관리하는 경계는 무엇인가요?",
                        "@Transactional 메서드가 런타임 예외를 던질 때 기본 롤백 동작은 무엇인가요?", "RELATED", "같은 애너테이션이지만 선언 목적과 롤백 규칙은 다릅니다."),
                sample("Spring 트랜잭션", "Spring의 @Transactional이 주로 관리하는 경계는 무엇인가요?",
                        "Spring Bean의 singleton scope는 인스턴스 개수를 어떻게 관리하나요?", "DISTINCT", "트랜잭션 경계와 Bean scope는 별개입니다."),

                sample("로드 밸런싱", "로드 밸런서의 헬스 체크가 필요한 주된 이유는 무엇인가요?",
                        "로드 밸런서가 요청을 전달할 수 있는 정상 서버만 고르기 위해 수행하는 검사는 무엇인가요?", "DUPLICATE", "동일한 헬스 체크의 목적을 묻습니다."),
                sample("로드 밸런싱", "로드 밸런서의 헬스 체크가 필요한 주된 이유는 무엇인가요?",
                        "라운드 로빈 방식은 여러 서버에 요청을 어떤 순서로 분배하나요?", "RELATED", "같은 로드 밸런싱이지만 대상 상태 확인과 분배 알고리즘은 다릅니다."),
                sample("로드 밸런싱", "로드 밸런서의 헬스 체크가 필요한 주된 이유는 무엇인가요?",
                        "DNS의 TTL은 어떤 동작에 영향을 주나요?", "DISTINCT", "서버 상태 확인과 DNS 캐시 만료는 다른 네트워크 기능입니다."),

                sample("CI/CD", "CI 파이프라인에서 테스트를 자동 실행하는 주된 목적은 무엇인가요?",
                        "코드 변경을 병합하거나 배포하기 전에 자동 검증을 수행하는 CI 단계의 목적은 무엇인가요?", "DUPLICATE", "동일한 CI 자동 테스트의 목적을 묻습니다."),
                sample("CI/CD", "CI 파이프라인에서 테스트를 자동 실행하는 주된 목적은 무엇인가요?",
                        "CD에서 blue-green 배포는 어떤 위험을 줄이기 위한 전략인가요?", "RELATED", "같은 전달 파이프라인이지만 CI 검증과 배포 전략은 다릅니다."),
                sample("CI/CD", "CI 파이프라인에서 테스트를 자동 실행하는 주된 목적은 무엇인가요?",
                        "Git rebase는 커밋 이력을 어떤 방식으로 정리하나요?", "DISTINCT", "CI 자동 검증과 Git 이력 정리는 다른 목적입니다.")
        );
    }

    private SampleDefinition sample(String topic, String questionA, String questionB,
                                    String referenceVerdict, String referenceRationale) {
        return new SampleDefinition(topic, questionA, questionB, referenceVerdict, referenceRationale);
    }

    private record SampleDefinition(String topic, String questionA, String questionB,
                                    String referenceVerdict, String referenceRationale) {
    }
}
