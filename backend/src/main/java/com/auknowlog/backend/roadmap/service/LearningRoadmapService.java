package com.auknowlog.backend.roadmap.service;

import com.auknowlog.backend.learning.entity.LearningAttempt;
import com.auknowlog.backend.learning.repository.LearningAttemptRepository;
import com.auknowlog.backend.learning.repository.LearningAttemptAnswerRepository;
import com.auknowlog.backend.roadmap.dto.ActiveLearningRoadmapResponse;
import com.auknowlog.backend.roadmap.dto.LearningRoadmapCollectionResponse;
import com.auknowlog.backend.roadmap.dto.LearningRoadmapSummary;
import com.auknowlog.backend.roadmap.dto.RoadmapMajorTopicProgress;
import com.auknowlog.backend.roadmap.dto.RoadmapLearningObjectiveDefinition;
import com.auknowlog.backend.roadmap.dto.RoadmapLearningObjectiveProgress;
import com.auknowlog.backend.roadmap.dto.RoadmapCreateRequest;
import com.auknowlog.backend.roadmap.dto.RoadmapDefinitionRequest;
import com.auknowlog.backend.roadmap.dto.RoadmapStepDefinition;
import com.auknowlog.backend.roadmap.dto.RoadmapStepProgress;
import com.auknowlog.backend.roadmap.dto.RoadmapSubtopicDefinition;
import com.auknowlog.backend.roadmap.dto.RoadmapSubtopicProgress;
import com.auknowlog.backend.roadmap.dto.RoadmapWeekProgress;
import com.auknowlog.backend.roadmap.entity.LearningRoadmap;
import com.auknowlog.backend.roadmap.entity.LearningRoadmapStep;
import com.auknowlog.backend.roadmap.entity.LearningRoadmapWeek;
import com.auknowlog.backend.roadmap.entity.LearningObjective;
import com.auknowlog.backend.roadmap.repository.LearningObjectiveRepository;
import com.auknowlog.backend.roadmap.repository.LearningRoadmapRepository;
import com.auknowlog.backend.roadmap.repository.LearningRoadmapStepRepository;
import com.auknowlog.backend.roadmap.repository.LearningRoadmapWeekRepository;
import com.auknowlog.backend.source.entity.SourceDocument;
import com.auknowlog.backend.source.repository.SourceDocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LearningRoadmapService {

    private static final int MAX_ROADMAP_FILE_BYTES = 128 * 1024;
    // 학습 설계용 할당량이 아니라 비정상·악성 payload로 인한 자원 고갈을 막는 절대 안전선이다.
    private static final int SAFETY_MAX_MAJOR_TOPICS = 10;
    private static final int SAFETY_MAX_SUBTOPICS_PER_MAJOR = 10;
    private static final int SAFETY_MAX_ATOMIC_STEPS = 100;
    private static final int SAFETY_MAX_QUESTION_TARGET_PER_UNIT = 30;
    private static final int SAFETY_MAX_OBJECTIVES_PER_UNIT = 10;
    private static final int SAFETY_MAX_QUESTIONS_PER_OBJECTIVE = 5;
    private static final int SAFETY_MAX_MAJOR_TOPIC_QUESTION_TARGET =
            SAFETY_MAX_SUBTOPICS_PER_MAJOR * SAFETY_MAX_QUESTION_TARGET_PER_UNIT;
    private static final Pattern STEP_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern OBJECTIVE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,48}");

    private final LearningRoadmapRepository learningRoadmapRepository;
    private final LearningRoadmapWeekRepository learningRoadmapWeekRepository;
    private final LearningRoadmapStepRepository learningRoadmapStepRepository;
    private final LearningAttemptRepository learningAttemptRepository;
    private final LearningAttemptAnswerRepository learningAttemptAnswerRepository;
    private final LearningObjectiveRepository learningObjectiveRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final ObjectMapper objectMapper;

    public LearningRoadmapService(LearningRoadmapRepository learningRoadmapRepository,
                                  LearningRoadmapWeekRepository learningRoadmapWeekRepository,
                                  LearningRoadmapStepRepository learningRoadmapStepRepository,
                                  LearningAttemptRepository learningAttemptRepository,
                                  LearningAttemptAnswerRepository learningAttemptAnswerRepository,
                                  LearningObjectiveRepository learningObjectiveRepository,
                                  SourceDocumentRepository sourceDocumentRepository,
                                  ObjectMapper objectMapper) {
        this.learningRoadmapRepository = learningRoadmapRepository;
        this.learningRoadmapWeekRepository = learningRoadmapWeekRepository;
        this.learningRoadmapStepRepository = learningRoadmapStepRepository;
        this.learningAttemptRepository = learningAttemptRepository;
        this.learningAttemptAnswerRepository = learningAttemptAnswerRepository;
        this.learningObjectiveRepository = learningObjectiveRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.objectMapper = objectMapper;
    }

    /** 기존 주차형 계획을 유지한다. 새 화면은 createStageBased를 기본으로 사용한다. */
    @Transactional
    public LearningRoadmapSummary create(RoadmapCreateRequest request) {
        String topic = request.topic().trim();
        int durationWeeks = request.durationWeeks();
        LearningRoadmap roadmap = learningRoadmapRepository.save(new LearningRoadmap(
                normalizedTitle(request.title(), topic, durationWeeks),
                topic,
                LocalDate.now(),
                durationWeeks,
                request.questionsPerWeek()
        ));

        for (int index = 0; index < durationWeeks; index++) {
            LocalDate weekStart = roadmap.getStartDate().plusWeeks(index);
            learningRoadmapWeekRepository.save(new LearningRoadmapWeek(
                    roadmap,
                    index + 1,
                    topic,
                    weekStart,
                    weekStart.plusDays(6),
                    request.questionsPerWeek()
            ));
        }
        return toSummary(roadmap);
    }

    /** 화면에서 구조화한 단계를 저장한다. 외부 모델이나 토큰을 사용하지 않는다. */
    @Transactional
    public LearningRoadmapSummary createStageBased(RoadmapDefinitionRequest request) {
        return createStageBased(request, "MANUAL", writeDefinition(request), null);
    }

    @Transactional
    public LearningRoadmapSummary createAiGenerated(RoadmapDefinitionRequest request) {
        return createAiGenerated(request, null);
    }

    @Transactional
    public LearningRoadmapSummary createAiGenerated(RoadmapDefinitionRequest request, Long sourceDocumentId) {
        SourceDocument sourceDocument = sourceDocumentId == null ? null : sourceDocumentRepository
                .findById(sourceDocumentId)
                .orElseThrow(() -> new NoSuchElementException("학습 자료를 찾을 수 없습니다."));
        return createStageBased(request, "AI_GENERATED", writeDefinition(request), sourceDocument);
    }

    /** AI가 만든 초안을 DB에 저장하지 않고 동일한 도메인 규칙으로 검증한다. */
    public void validateDraft(RoadmapDefinitionRequest request) {
        validateDefinition(request);
    }

    /** *.roadmap.json 파일은 데이터로만 읽고, HTML/Mermaid 실행 없이 계약을 검증한다. */
    @Transactional
    public LearningRoadmapSummary importStageBased(String originalFilename, byte[] fileContent) {
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".roadmap.json")) {
            throw new IllegalArgumentException("로드맵 파일은 .roadmap.json 확장자만 가져올 수 있습니다.");
        }
        if (fileContent == null || fileContent.length == 0) {
            throw new IllegalArgumentException("가져올 로드맵 파일이 비어 있습니다.");
        }
        if (fileContent.length > MAX_ROADMAP_FILE_BYTES) {
            throw new IllegalArgumentException("로드맵 파일은 128KB 이하여야 합니다.");
        }

        String rawJson = new String(fileContent, StandardCharsets.UTF_8);
        try {
            RoadmapDefinitionRequest definition = objectMapper.readerFor(RoadmapDefinitionRequest.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawJson);
            return createStageBased(definition, "FILE_IMPORT", rawJson, null);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("로드맵 파일 형식이 올바른 JSON이 아닙니다.");
        }
    }

    @Transactional(readOnly = true)
    public ActiveLearningRoadmapResponse getActive() {
        LearningRoadmap activeRoadmap = learningRoadmapRepository.findByStatusOrderByCreatedAtDesc("ACTIVE")
                .stream()
                .findFirst()
                .orElse(null);
        return new ActiveLearningRoadmapResponse(activeRoadmap == null ? null : toSummary(activeRoadmap));
    }

    @Transactional(readOnly = true)
    public LearningRoadmapCollectionResponse getRoadmaps() {
        return new LearningRoadmapCollectionResponse(
                learningRoadmapRepository.findByStatusOrderByCreatedAtDesc("ACTIVE")
                        .stream()
                        .map(this::toSummary)
                        .toList(),
                learningRoadmapRepository.findByStatusOrderByCreatedAtDesc("COMPLETED")
                        .stream()
                        .map(this::toSummary)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public LearningRoadmapSummary getById(Long roadmapId) {
        LearningRoadmap roadmap = learningRoadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new NoSuchElementException("학습 로드맵을 찾을 수 없습니다."));
        return toSummary(roadmap);
    }

    /**
     * 목표 문항을 모두 푼 뒤에만 다음 단계를 명시적으로 연다.
     * 추가 학습을 하더라도 이 승인이 있기 전에는 선행 조건이 충족되지 않는다.
     */
    @Transactional
    public LearningRoadmapSummary confirmStepAdvance(Long roadmapId, Long stepId) {
        LearningRoadmap roadmap = learningRoadmapRepository.findById(roadmapId)
                .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(() -> new NoSuchElementException("진행 중인 학습 로드맵을 찾을 수 없습니다."));
        LearningRoadmapStep step = learningRoadmapStepRepository.findById(stepId)
                .filter(candidate -> candidate.getRoadmap().getId().equals(roadmap.getId()))
                .orElseThrow(() -> new NoSuchElementException("선택한 로드맵에 속한 학습 단계를 찾을 수 없습니다."));
        long completedQuestions = learningAttemptRepository.findRoadmapStepCompletedQuestions(roadmapId).stream()
                .filter(row -> ((Number) row[0]).longValue() == stepId)
                .mapToLong(row -> ((Number) row[1]).longValue())
                .findFirst()
                .orElse(0);
        if (completedQuestions < step.getQuestionTarget()) {
            throw new IllegalArgumentException("완료 기준 문제를 모두 푼 뒤 다음 단계로 진행할 수 있습니다.");
        }
        boolean prerequisitesConfirmed = step.getPrerequisites().stream()
                .allMatch(prerequisite -> prerequisite.getAdvanceConfirmedAt() != null);
        if (!prerequisitesConfirmed) {
            throw new IllegalArgumentException("선행 학습 단계를 먼저 완료해주세요.");
        }
        step.confirmAdvance();
        LearningRoadmapSummary summary = toSummary(roadmap);
        if (summary.completed()) {
            roadmap.complete();
            return toSummary(roadmap);
        }
        return summary;
    }

    /**
     * 로드맵 정의와 단계만 삭제한다. 이미 저장된 풀이 이력은 학습 기록으로 남기고
     * DB FK의 ON DELETE SET NULL 규칙으로 로드맵 연결만 해제한다.
     */
    @Transactional
    public void deleteRoadmap(Long roadmapId) {
        LearningRoadmap roadmap = learningRoadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new NoSuchElementException("삭제할 학습 로드맵을 찾을 수 없습니다."));
        learningRoadmapRepository.delete(roadmap);
        learningRoadmapRepository.flush();
    }

    @Transactional
    public void completeIfSatisfied(Long roadmapId) {
        LearningRoadmap roadmap = learningRoadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new NoSuchElementException("학습 로드맵을 찾을 수 없습니다."));
        if ("ACTIVE".equals(roadmap.getStatus()) && toSummary(roadmap).completed()) {
            roadmap.complete();
        }
    }

    /** 이전 버전에서 상태 갱신 없이 완료되었거나 자동 ARCHIVED된 데이터를 시작 시 한 번 정리한다. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileCompletedRoadmaps() {
        List<LearningRoadmap> candidates = new java.util.ArrayList<>(
                learningRoadmapRepository.findByStatusOrderByCreatedAtDesc("ACTIVE")
        );
        candidates.addAll(learningRoadmapRepository.findByStatusOrderByCreatedAtDesc("ARCHIVED"));
        for (LearningRoadmap roadmap : candidates) {
            if (toSummary(roadmap).completed()) {
                roadmap.complete();
            }
        }
    }

    private LearningRoadmapSummary createStageBased(RoadmapDefinitionRequest request, String sourceType,
                                                     String rawDefinitionJson, SourceDocument sourceDocument) {
        validateDefinition(request);
        String topic = request.topic().trim();
        int durationWeeks = request.durationWeeks();
        long totalTarget = request.steps().stream().mapToLong(this::questionTarget).sum();
        int questionsPerWeek = (int) Math.min(20, Math.max(1,
                (long) Math.ceil((double) totalTarget / durationWeeks)));
        LearningRoadmap roadmap = learningRoadmapRepository.save(new LearningRoadmap(
                request.title().trim(),
                topic,
                blankToNull(request.description()),
                sourceType,
                rawDefinitionJson,
                LocalDate.now(),
                durationWeeks,
                questionsPerWeek,
                sourceDocument
        ));

        Map<String, List<LearningRoadmapStep>> atomicStepsByMajorKey = new LinkedHashMap<>();
        int stepOrder = 1;
        for (RoadmapStepDefinition majorTopic : request.steps()) {
            String majorKey = majorTopic.key().trim();
            String majorTitle = majorTopic.title().trim();
            String majorDescription = blankToNull(majorTopic.description());
            String majorTopicValue = blankToNull(majorTopic.topic()) == null ? topic : majorTopic.topic().trim();
            List<LearningRoadmapStep> atomicSteps;
            if (majorTopic.safeSubtopics().isEmpty()) {
                LearningRoadmapStep storedStep = learningRoadmapStepRepository.save(new LearningRoadmapStep(
                        roadmap, majorKey, majorTitle, majorDescription, majorTopicValue,
                        majorTopic.questionTarget(), stepOrder++, majorKey, majorTitle, majorDescription,
                        majorTopicValue, null, null
                ));
                saveLearningObjectives(storedStep, majorTopic.safeLearningObjectives());
                atomicSteps = List.of(storedStep);
            } else {
                java.util.ArrayList<LearningRoadmapStep> expanded = new java.util.ArrayList<>();
                for (RoadmapSubtopicDefinition subtopic : majorTopic.safeSubtopics()) {
                    String subtopicTopic = blankToNull(subtopic.topic()) == null
                            ? majorTopicValue + " " + subtopic.title().trim()
                            : subtopic.topic().trim();
                    LearningRoadmapStep storedStep = learningRoadmapStepRepository.save(new LearningRoadmapStep(
                            roadmap,
                            atomicStepKey(majorKey, subtopic.key().trim()),
                            subtopic.title().trim(),
                            blankToNull(subtopic.description()),
                            subtopicTopic,
                            subtopic.questionTarget(),
                            stepOrder++,
                            majorKey,
                            majorTitle,
                            majorDescription,
                            majorTopicValue,
                            subtopic.key().trim(),
                            subtopic.title().trim()
                    ));
                    saveLearningObjectives(storedStep, subtopic.safeLearningObjectives());
                    expanded.add(storedStep);
                }
                atomicSteps = List.copyOf(expanded);
            }
            atomicStepsByMajorKey.put(majorKey, atomicSteps);
        }
        for (RoadmapStepDefinition majorTopic : request.steps()) {
            List<LearningRoadmapStep> atomicSteps = atomicStepsByMajorKey.get(majorTopic.key().trim());
            LearningRoadmapStep first = atomicSteps.getFirst();
            for (String prerequisiteKey : majorTopic.safeDependsOn()) {
                List<LearningRoadmapStep> prerequisiteSteps = atomicStepsByMajorKey.get(prerequisiteKey.trim());
                first.addPrerequisite(prerequisiteSteps.getLast());
            }
            for (int index = 1; index < atomicSteps.size(); index++) {
                atomicSteps.get(index).addPrerequisite(atomicSteps.get(index - 1));
            }
        }
        learningRoadmapStepRepository.saveAll(atomicStepsByMajorKey.values().stream().flatMap(List::stream).toList());
        return toSummary(roadmap);
    }

    private LearningRoadmapSummary toSummary(LearningRoadmap roadmap) {
        List<LearningRoadmapStep> steps = learningRoadmapStepRepository
                .findWithPrerequisitesByRoadmapIdOrderByStepOrder(roadmap.getId());
        if (!steps.isEmpty()) {
            return stageSummary(roadmap, steps);
        }
        return weeklySummary(roadmap);
    }

    private LearningRoadmapSummary stageSummary(LearningRoadmap roadmap, List<LearningRoadmapStep> steps) {
        Map<Long, Long> completedByStepId = learningAttemptRepository
                .findRoadmapStepCompletedQuestions(roadmap.getId())
                .stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()
                ));
        Map<Long, List<LearningObjective>> objectivesByStepId = learningObjectiveRepository
                .findByRoadmapStepRoadmapIdOrderByRoadmapStepStepOrderAscObjectiveOrderAsc(roadmap.getId())
                .stream()
                .collect(Collectors.groupingBy(
                        objective -> objective.getRoadmapStep().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, long[]> objectiveProgressById = learningAttemptAnswerRepository
                .findRoadmapObjectiveProgress(roadmap.getId())
                .stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()}
                ));
        Set<String> completedKeys = steps.stream()
                .filter(step -> step.getAdvanceConfirmedAt() != null)
                .map(LearningRoadmapStep::getStepKey)
                .collect(Collectors.toSet());

        List<RoadmapStepProgress> stepProgress = steps.stream()
                .map(step -> toStepProgress(
                        step,
                        completedByStepId.getOrDefault(step.getId(), 0L),
                        completedKeys,
                        objectivesByStepId.getOrDefault(step.getId(), List.of()),
                        objectiveProgressById
                ))
                .toList();
        List<RoadmapMajorTopicProgress> majorTopics = toMajorTopicProgress(steps, stepProgress);
        long totalPlanned = steps.stream().mapToLong(LearningRoadmapStep::getQuestionTarget).sum();
        long completed = stepProgress.stream().mapToLong(RoadmapStepProgress::completedQuestions).sum();
        String currentStepKey = stepProgress.stream()
                .filter(step -> "IN_PROGRESS".equals(step.status()) || "READY".equals(step.status()))
                .map(RoadmapStepProgress::key)
                .findFirst()
                .orElse(null);

        return new LearningRoadmapSummary(
                roadmap.getId(), roadmap.getTitle(), roadmap.getTopic(), roadmap.getStatus(),
                roadmap.getStartDate(), roadmap.getEndDate(), roadmap.getDurationWeeks(), roadmap.getQuestionsPerWeek(),
                totalPlanned, completed, percentage(completed, totalPlanned), null,
                !stepProgress.isEmpty() && stepProgress.stream().allMatch(step -> "COMPLETED".equals(step.status())), List.of(),
                roadmap.getSourceType(), roadmap.getDescription(), currentStepKey, stepProgress, majorTopics,
                sourceDocumentId(roadmap), sourceDocumentTitle(roadmap), sourceDocumentUri(roadmap)
        );
    }

    private RoadmapStepProgress toStepProgress(LearningRoadmapStep step, long rawCompleted,
                                                Set<String> completedKeys,
                                                List<LearningObjective> objectives,
                                                Map<Long, long[]> objectiveProgressById) {
        long completed = Math.min(rawCompleted, step.getQuestionTarget());
        long additionalPracticeQuestions = Math.max(0, rawCompleted - step.getQuestionTarget());
        List<String> prerequisiteKeys = step.getPrerequisites().stream()
                .map(LearningRoadmapStep::getStepKey)
                .sorted()
                .toList();
        boolean prerequisitesCompleted = prerequisiteKeys.stream().allMatch(completedKeys::contains);
        String status;
        boolean goalsSatisfied = completed >= step.getQuestionTarget();
        boolean advanceConfirmed = step.getAdvanceConfirmedAt() != null;
        if (goalsSatisfied && advanceConfirmed) {
            status = "COMPLETED";
        } else if (goalsSatisfied) {
            status = "AWAITING_DECISION";
        } else if (!prerequisitesCompleted) {
            status = "LOCKED";
        } else if (completed == 0) {
            status = "READY";
        } else {
            status = "IN_PROGRESS";
        }
        return new RoadmapStepProgress(
                step.getId(), step.getStepKey(), step.getTitle(), step.getDescription(), step.getTopic(),
                step.getQuestionTarget(), completed, percentage(completed, step.getQuestionTarget()), status,
                prerequisiteKeys,
                objectives.stream()
                        .map(objective -> toObjectiveProgress(objective, objectiveProgressById.get(objective.getId())))
                        .toList(),
                additionalPracticeQuestions, advanceConfirmed, "AWAITING_DECISION".equals(status)
        );
    }

    private LearningRoadmapSummary weeklySummary(LearningRoadmap roadmap) {
        List<LearningRoadmapWeek> weeks = learningRoadmapWeekRepository.findByRoadmapIdOrderByWeekNumber(roadmap.getId());
        Map<LocalDate, Long> completedQuestionsByDate = learningAttemptRepository.findRoadmapAttemptsBetween(
                        roadmap.getId(), roadmap.getStartDate().atStartOfDay(),
                        roadmap.getEndDate().plusDays(1).atStartOfDay())
                .stream()
                .collect(Collectors.groupingBy(
                        attempt -> attempt.getSubmittedAt().toLocalDate(),
                        Collectors.summingLong(LearningAttempt::getTotalQuestions)
                ));

        LocalDate today = LocalDate.now();
        List<RoadmapWeekProgress> weekProgress = weeks.stream()
                .map(week -> toWeekProgress(week, completedQuestionsByDate, today))
                .toList();
        long totalPlanned = weeks.stream().mapToLong(LearningRoadmapWeek::getPlannedQuestions).sum();
        long completed = weekProgress.stream().mapToLong(RoadmapWeekProgress::completedQuestions).sum();
        Integer currentWeekNumber = weekProgress.stream()
                .filter(week -> "CURRENT".equals(week.status()))
                .map(RoadmapWeekProgress::weekNumber)
                .findFirst()
                .orElseGet(() -> weekProgress.stream()
                        .filter(week -> !"COMPLETED".equals(week.status()))
                        .map(RoadmapWeekProgress::weekNumber)
                        .findFirst()
                        .orElse(null));

        return new LearningRoadmapSummary(
                roadmap.getId(), roadmap.getTitle(), roadmap.getTopic(), roadmap.getStatus(),
                roadmap.getStartDate(), roadmap.getEndDate(), roadmap.getDurationWeeks(), roadmap.getQuestionsPerWeek(),
                totalPlanned, completed, percentage(completed, totalPlanned), currentWeekNumber,
                totalPlanned > 0 && completed >= totalPlanned, weekProgress,
                roadmap.getSourceType(), roadmap.getDescription(), null, List.of(), List.of(),
                sourceDocumentId(roadmap), sourceDocumentTitle(roadmap), sourceDocumentUri(roadmap)
        );
    }

    private Long sourceDocumentId(LearningRoadmap roadmap) {
        return roadmap.getSourceDocument() == null ? null : roadmap.getSourceDocument().getId();
    }

    private String sourceDocumentTitle(LearningRoadmap roadmap) {
        return roadmap.getSourceDocument() == null ? null : roadmap.getSourceDocument().getTitle();
    }

    private String sourceDocumentUri(LearningRoadmap roadmap) {
        return roadmap.getSourceDocument() == null ? null : roadmap.getSourceDocument().getSourceUri();
    }

    private List<RoadmapMajorTopicProgress> toMajorTopicProgress(List<LearningRoadmapStep> steps,
                                                                 List<RoadmapStepProgress> stepProgress) {
        Map<Long, RoadmapStepProgress> progressByStepId = stepProgress.stream()
                .collect(Collectors.toMap(RoadmapStepProgress::stepId, progress -> progress));
        Map<String, List<LearningRoadmapStep>> byMajorTopic = steps.stream().collect(Collectors.groupingBy(
                LearningRoadmapStep::getMajorTopicKey,
                LinkedHashMap::new,
                Collectors.toList()
        ));
        return byMajorTopic.values().stream().map(atomicSteps -> {
            LearningRoadmapStep first = atomicSteps.getFirst();
            List<RoadmapStepProgress> atomicProgress = atomicSteps.stream()
                    .map(step -> progressByStepId.get(step.getId()))
                    .toList();
            long target = atomicSteps.stream().mapToLong(LearningRoadmapStep::getQuestionTarget).sum();
            long completed = atomicProgress.stream().mapToLong(RoadmapStepProgress::completedQuestions).sum();
            String status = majorTopicStatus(atomicProgress, completed, target);
            List<String> prerequisiteKeys = first.getPrerequisites().stream()
                    .map(LearningRoadmapStep::getMajorTopicKey)
                    .distinct()
                    .sorted()
                    .toList();
            boolean hasSubtopics = first.getSubtopicKey() != null;
            List<RoadmapSubtopicProgress> subtopics = hasSubtopics
                    ? java.util.stream.IntStream.range(0, atomicSteps.size())
                    .mapToObj(index -> toSubtopicProgress(atomicSteps.get(index), atomicProgress.get(index)))
                    .toList()
                    : List.of();
            return new RoadmapMajorTopicProgress(
                    hasSubtopics ? null : first.getId(),
                    first.getMajorTopicKey(), first.getMajorTopicTitle(), first.getMajorTopicDescription(),
                    first.getMajorTopicTopic(), (int) target, completed, percentage(completed, target), status,
                    prerequisiteKeys, subtopics,
                    hasSubtopics ? List.of() : atomicProgress.getFirst().learningObjectives()
            );
        }).toList();
    }

    private RoadmapSubtopicProgress toSubtopicProgress(LearningRoadmapStep step, RoadmapStepProgress progress) {
        return new RoadmapSubtopicProgress(
                step.getId(), step.getSubtopicKey(), step.getSubtopicTitle(), step.getDescription(), step.getTopic(),
                step.getQuestionTarget(), progress.completedQuestions(), progress.progressPercent(), progress.status(),
                progress.learningObjectives()
        );
    }

    private RoadmapLearningObjectiveProgress toObjectiveProgress(LearningObjective objective, long[] rawProgress) {
        long covered = rawProgress == null ? 0 : rawProgress[0];
        long correct = rawProgress == null ? 0 : rawProgress[1];
        return new RoadmapLearningObjectiveProgress(
                objective.getId(), objective.getObjectiveKey(), objective.getTitle(), objective.getDescription(),
                objective.getImportance(), objective.getTargetQuestionCount(), covered, correct,
                percentage(Math.min(covered, objective.getTargetQuestionCount()), objective.getTargetQuestionCount())
        );
    }

    private String majorTopicStatus(List<RoadmapStepProgress> atomicProgress, long completed, long target) {
        if (atomicProgress.stream().allMatch(step -> "COMPLETED".equals(step.status()))) return "COMPLETED";
        if (atomicProgress.stream().allMatch(step -> "LOCKED".equals(step.status()))) return "LOCKED";
        if (completed > 0 || atomicProgress.stream().anyMatch(step -> "IN_PROGRESS".equals(step.status()))) {
            return "IN_PROGRESS";
        }
        return "READY";
    }

    private RoadmapWeekProgress toWeekProgress(LearningRoadmapWeek week,
                                                Map<LocalDate, Long> completedQuestionsByDate,
                                                LocalDate today) {
        long completed = completedQuestionsByDate.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(week.getWeekStart())
                        && !entry.getKey().isAfter(week.getWeekEnd()))
                .mapToLong(Map.Entry::getValue)
                .sum();
        return new RoadmapWeekProgress(
                week.getWeekNumber(), week.getTopic(), week.getWeekStart(), week.getWeekEnd(),
                week.getPlannedQuestions(), completed, percentage(completed, week.getPlannedQuestions()),
                weekStatus(week, completed, today)
        );
    }

    private String weekStatus(LearningRoadmapWeek week, long completed, LocalDate today) {
        if (completed >= week.getPlannedQuestions()) return "COMPLETED";
        if (today.isBefore(week.getWeekStart())) return "UPCOMING";
        if (today.isAfter(week.getWeekEnd())) return "OVERDUE";
        return "CURRENT";
    }

    private void validateDefinition(RoadmapDefinitionRequest request) {
        if (request == null || !("1.0".equals(request.version()) || "1.1".equals(request.version())
                || "1.2".equals(request.version())) || isBlank(request.title())
                || request.title().length() > 120 || isBlank(request.topic()) || request.topic().length() > 120
                || (request.description() != null && request.description().length() > 2000)
                || request.durationWeeks() == null || request.durationWeeks() < 1 || request.durationWeeks() > 52
                || request.steps() == null || request.steps().isEmpty()
                || request.steps().size() > SAFETY_MAX_MAJOR_TOPICS) {
            throw new IllegalArgumentException("버전 1.0, 1.1 또는 1.2의 로드맵 이름, 주제, 기간과 대주제를 확인해주세요.");
        }
        Map<String, RoadmapStepDefinition> byKey = new LinkedHashMap<>();
        for (RoadmapStepDefinition step : request.steps()) {
            int maximumQuestionTarget = step != null && !step.safeSubtopics().isEmpty()
                    ? SAFETY_MAX_MAJOR_TOPIC_QUESTION_TARGET
                    : SAFETY_MAX_QUESTION_TARGET_PER_UNIT;
            if (step == null || isBlank(step.key()) || !STEP_KEY.matcher(step.key().trim()).matches()
                    || isBlank(step.title()) || step.title().trim().length() > 120
                    || step.questionTarget() == null || step.questionTarget() < 1
                    || step.questionTarget() > maximumQuestionTarget
                    || (step.description() != null && step.description().length() > 1000)
                    || (step.topic() != null && step.topic().length() > 120)) {
                throw new IllegalArgumentException("단계 식별자, 이름, 주제와 목표 문제 수를 확인해주세요.");
            }
            String key = step.key().trim();
            if (byKey.put(key, step) != null) {
                throw new IllegalArgumentException("단계 식별자 '" + key + "'가 중복되었습니다.");
            }
            validateSubtopics(step);
            if (step.safeSubtopics().isEmpty()) {
                validateLearningObjectives(step.safeLearningObjectives(), step.questionTarget(), step.key());
            } else if (!step.safeLearningObjectives().isEmpty()) {
                throw new IllegalArgumentException("소주제가 있는 대주제에는 대주제 학습 목표를 함께 둘 수 없습니다.");
            }
        }
        int atomicStepCount = request.steps().stream()
                .mapToInt(step -> Math.max(1, step.safeSubtopics().size()))
                .sum();
        if (atomicStepCount > SAFETY_MAX_ATOMIC_STEPS) {
            throw new IllegalArgumentException("로드맵 학습 단위가 비정상적으로 많습니다. 주제를 나누어 다시 생성해주세요.");
        }
        for (Map.Entry<String, RoadmapStepDefinition> entry : byKey.entrySet()) {
            Set<String> dependencies = new HashSet<>();
            for (String dependency : entry.getValue().safeDependsOn()) {
                if (isBlank(dependency) || !byKey.containsKey(dependency.trim())) {
                    throw new IllegalArgumentException("단계 '" + entry.getKey() + "'의 선행 단계를 찾을 수 없습니다.");
                }
                if (entry.getKey().equals(dependency.trim()) || !dependencies.add(dependency.trim())) {
                    throw new IllegalArgumentException("단계 '" + entry.getKey() + "'의 선행 단계 설정이 올바르지 않습니다.");
                }
            }
        }
        Map<String, Set<String>> graph = byKey.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().safeDependsOn().stream().map(String::trim).collect(Collectors.toSet())
        ));
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String key : graph.keySet()) {
            if (hasCycle(key, graph, visiting, visited)) {
                throw new IllegalArgumentException("선행 단계에 순환 참조가 있습니다.");
            }
        }
    }

    private void validateSubtopics(RoadmapStepDefinition majorTopic) {
        if (majorTopic.safeSubtopics().size() > SAFETY_MAX_SUBTOPICS_PER_MAJOR) {
            throw new IllegalArgumentException("한 대주제의 소주제가 비정상적으로 많습니다. 대주제를 나누어주세요.");
        }
        Set<String> keys = new HashSet<>();
        for (RoadmapSubtopicDefinition subtopic : majorTopic.safeSubtopics()) {
            if (subtopic == null || isBlank(subtopic.key()) || !STEP_KEY.matcher(subtopic.key().trim()).matches()
                    || isBlank(subtopic.title()) || subtopic.title().trim().length() > 120
                    || subtopic.questionTarget() == null || subtopic.questionTarget() < 1
                    || subtopic.questionTarget() > SAFETY_MAX_QUESTION_TARGET_PER_UNIT
                    || (subtopic.description() != null && subtopic.description().length() > 1000)
                    || (subtopic.topic() != null && subtopic.topic().length() > 120)
                    || !STEP_KEY.matcher(atomicStepKey(majorTopic.key().trim(), subtopic.key().trim())).matches()) {
                throw new IllegalArgumentException("소주제 식별자, 이름, 문제 생성 주제와 목표 문제 수를 확인해주세요.");
            }
            if (!keys.add(subtopic.key().trim())) {
                throw new IllegalArgumentException("대주제 '" + majorTopic.key().trim() + "'의 소주제 식별자가 중복되었습니다.");
            }
            validateLearningObjectives(
                    subtopic.safeLearningObjectives(), subtopic.questionTarget(),
                    majorTopic.key().trim() + "/" + subtopic.key().trim()
            );
        }
        if (!majorTopic.safeSubtopics().isEmpty()) {
            int subtopicQuestionTarget = majorTopic.safeSubtopics().stream()
                    .mapToInt(RoadmapSubtopicDefinition::questionTarget)
                    .sum();
            if (majorTopic.questionTarget() != subtopicQuestionTarget) {
                throw new IllegalArgumentException("대주제 '" + majorTopic.key().trim()
                        + "'의 목표 문제 수는 소주제 목표 문제 수의 합과 같아야 합니다.");
            }
        }
    }

    private void validateLearningObjectives(List<RoadmapLearningObjectiveDefinition> objectives,
                                            int questionTarget, String unitKey) {
        if (objectives.isEmpty()) return;
        if (objectives.size() > SAFETY_MAX_OBJECTIVES_PER_UNIT) {
            throw new IllegalArgumentException("학습 단위 '" + unitKey + "'의 학습 목표가 너무 많습니다.");
        }
        Set<String> keys = new HashSet<>();
        int allocatedQuestions = 0;
        for (RoadmapLearningObjectiveDefinition objective : objectives) {
            if (objective == null || isBlank(objective.key()) || !OBJECTIVE_KEY.matcher(objective.key().trim()).matches()
                    || isBlank(objective.title()) || objective.title().trim().length() > 120
                    || (objective.description() != null && objective.description().length() > 500)
                    || !("CORE".equals(objective.normalizedImportance())
                    || "SUPPORTING".equals(objective.normalizedImportance()))
                    || objective.targetQuestionCount() == null || objective.targetQuestionCount() < 1
                    || objective.targetQuestionCount() > SAFETY_MAX_QUESTIONS_PER_OBJECTIVE) {
                throw new IllegalArgumentException("학습 단위 '" + unitKey + "'의 필수 학습 목표를 확인해주세요.");
            }
            if (!keys.add(objective.key().trim())) {
                throw new IllegalArgumentException("학습 단위 '" + unitKey + "'의 학습 목표 식별자가 중복되었습니다.");
            }
            allocatedQuestions += objective.targetQuestionCount();
        }
        if (allocatedQuestions != questionTarget) {
            throw new IllegalArgumentException("학습 단위 '" + unitKey
                    + "'의 목표 문제 수는 필수 학습 목표별 문제 수의 합과 같아야 합니다.");
        }
    }

    private void saveLearningObjectives(LearningRoadmapStep step,
                                        List<RoadmapLearningObjectiveDefinition> definitions) {
        for (int index = 0; index < definitions.size(); index++) {
            RoadmapLearningObjectiveDefinition definition = definitions.get(index);
            learningObjectiveRepository.save(new LearningObjective(
                    step,
                    definition.key().trim(),
                    definition.title().trim(),
                    blankToNull(definition.description()),
                    definition.normalizedImportance(),
                    definition.targetQuestionCount(),
                    index + 1
            ));
        }
    }

    private String atomicStepKey(String majorTopicKey, String subtopicKey) {
        return majorTopicKey + "__" + subtopicKey;
    }

    private long questionTarget(RoadmapStepDefinition majorTopic) {
        if (majorTopic.safeSubtopics().isEmpty()) return majorTopic.questionTarget();
        return majorTopic.safeSubtopics().stream().mapToLong(RoadmapSubtopicDefinition::questionTarget).sum();
    }

    private boolean hasCycle(String key, Map<String, Set<String>> graph, Set<String> visiting, Set<String> visited) {
        if (visited.contains(key)) return false;
        if (!visiting.add(key)) return true;
        for (String dependency : graph.get(key)) {
            if (hasCycle(dependency, graph, visiting, visited)) return true;
        }
        visiting.remove(key);
        visited.add(key);
        return false;
    }

    private String writeDefinition(RoadmapDefinitionRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("로드맵 정의를 저장할 수 없습니다.", e);
        }
    }

    private int percentage(long numerator, long denominator) {
        if (denominator == 0) return 0;
        return (int) Math.min(100, Math.round((double) numerator * 100 / denominator));
    }

    private String normalizedTitle(String requestedTitle, String topic, int durationWeeks) {
        return isBlank(requestedTitle) ? topic + " " + durationWeeks + "주 학습 로드맵" : requestedTitle.trim();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
