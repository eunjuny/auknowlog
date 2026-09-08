package com.auknowlog.backend.roadmap.service;

import com.auknowlog.backend.learning.entity.LearningAttempt;
import com.auknowlog.backend.learning.repository.LearningAttemptRepository;
import com.auknowlog.backend.roadmap.dto.ActiveLearningRoadmapResponse;
import com.auknowlog.backend.roadmap.dto.LearningRoadmapSummary;
import com.auknowlog.backend.roadmap.dto.RoadmapCreateRequest;
import com.auknowlog.backend.roadmap.dto.RoadmapDefinitionRequest;
import com.auknowlog.backend.roadmap.dto.RoadmapStepDefinition;
import com.auknowlog.backend.roadmap.dto.RoadmapStepProgress;
import com.auknowlog.backend.roadmap.dto.RoadmapWeekProgress;
import com.auknowlog.backend.roadmap.entity.LearningRoadmap;
import com.auknowlog.backend.roadmap.entity.LearningRoadmapStep;
import com.auknowlog.backend.roadmap.entity.LearningRoadmapWeek;
import com.auknowlog.backend.roadmap.repository.LearningRoadmapRepository;
import com.auknowlog.backend.roadmap.repository.LearningRoadmapStepRepository;
import com.auknowlog.backend.roadmap.repository.LearningRoadmapWeekRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final Pattern STEP_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final LearningRoadmapRepository learningRoadmapRepository;
    private final LearningRoadmapWeekRepository learningRoadmapWeekRepository;
    private final LearningRoadmapStepRepository learningRoadmapStepRepository;
    private final LearningAttemptRepository learningAttemptRepository;
    private final ObjectMapper objectMapper;

    public LearningRoadmapService(LearningRoadmapRepository learningRoadmapRepository,
                                  LearningRoadmapWeekRepository learningRoadmapWeekRepository,
                                  LearningRoadmapStepRepository learningRoadmapStepRepository,
                                  LearningAttemptRepository learningAttemptRepository,
                                  ObjectMapper objectMapper) {
        this.learningRoadmapRepository = learningRoadmapRepository;
        this.learningRoadmapWeekRepository = learningRoadmapWeekRepository;
        this.learningRoadmapStepRepository = learningRoadmapStepRepository;
        this.learningAttemptRepository = learningAttemptRepository;
        this.objectMapper = objectMapper;
    }

    /** 기존 주차형 계획을 유지한다. 새 화면은 createStageBased를 기본으로 사용한다. */
    @Transactional
    public LearningRoadmapSummary create(RoadmapCreateRequest request) {
        archiveActiveRoadmaps();

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
        return createStageBased(request, "MANUAL", writeDefinition(request));
    }

    @Transactional
    public LearningRoadmapSummary createAiGenerated(RoadmapDefinitionRequest request) {
        return createStageBased(request, "AI_GENERATED", writeDefinition(request));
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
            return createStageBased(definition, "FILE_IMPORT", rawJson);
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
    public LearningRoadmapSummary getById(Long roadmapId) {
        LearningRoadmap roadmap = learningRoadmapRepository.findById(roadmapId)
                .orElseThrow(() -> new NoSuchElementException("학습 로드맵을 찾을 수 없습니다."));
        return toSummary(roadmap);
    }

    private LearningRoadmapSummary createStageBased(RoadmapDefinitionRequest request, String sourceType,
                                                     String rawDefinitionJson) {
        validateDefinition(request);
        archiveActiveRoadmaps();

        String topic = request.topic().trim();
        int durationWeeks = request.durationWeeks();
        long totalTarget = request.steps().stream().mapToLong(RoadmapStepDefinition::questionTarget).sum();
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
                questionsPerWeek
        ));

        Map<String, LearningRoadmapStep> stepsByKey = new LinkedHashMap<>();
        for (int index = 0; index < request.steps().size(); index++) {
            RoadmapStepDefinition definition = request.steps().get(index);
            LearningRoadmapStep step = learningRoadmapStepRepository.save(new LearningRoadmapStep(
                    roadmap,
                    definition.key().trim(),
                    definition.title().trim(),
                    blankToNull(definition.description()),
                    blankToNull(definition.topic()) == null ? topic : definition.topic().trim(),
                    definition.questionTarget(),
                    index + 1
            ));
            stepsByKey.put(step.getStepKey(), step);
        }
        for (RoadmapStepDefinition definition : request.steps()) {
            LearningRoadmapStep step = stepsByKey.get(definition.key().trim());
            for (String prerequisiteKey : definition.safeDependsOn()) {
                step.addPrerequisite(stepsByKey.get(prerequisiteKey.trim()));
            }
        }
        learningRoadmapStepRepository.saveAll(stepsByKey.values());
        return toSummary(roadmap);
    }

    private void archiveActiveRoadmaps() {
        learningRoadmapRepository.findByStatusOrderByCreatedAtDesc("ACTIVE")
                .forEach(LearningRoadmap::archive);
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
        Set<String> completedKeys = steps.stream()
                .filter(step -> completedByStepId.getOrDefault(step.getId(), 0L) >= step.getQuestionTarget())
                .map(LearningRoadmapStep::getStepKey)
                .collect(Collectors.toSet());

        List<RoadmapStepProgress> stepProgress = steps.stream()
                .map(step -> toStepProgress(step, completedByStepId.getOrDefault(step.getId(), 0L), completedKeys))
                .toList();
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
                totalPlanned > 0 && completed >= totalPlanned, List.of(),
                roadmap.getSourceType(), roadmap.getDescription(), currentStepKey, stepProgress
        );
    }

    private RoadmapStepProgress toStepProgress(LearningRoadmapStep step, long rawCompleted,
                                                Set<String> completedKeys) {
        long completed = Math.min(rawCompleted, step.getQuestionTarget());
        List<String> prerequisiteKeys = step.getPrerequisites().stream()
                .map(LearningRoadmapStep::getStepKey)
                .sorted()
                .toList();
        boolean prerequisitesCompleted = prerequisiteKeys.stream().allMatch(completedKeys::contains);
        String status;
        if (completed >= step.getQuestionTarget()) {
            status = "COMPLETED";
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
                prerequisiteKeys
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
                roadmap.getSourceType(), roadmap.getDescription(), null, List.of()
        );
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
        if (request == null || !"1.0".equals(request.version()) || isBlank(request.title())
                || request.title().length() > 120 || isBlank(request.topic()) || request.topic().length() > 120
                || (request.description() != null && request.description().length() > 2000)
                || request.durationWeeks() == null || request.durationWeeks() < 1 || request.durationWeeks() > 52
                || request.steps() == null || request.steps().isEmpty() || request.steps().size() > 20) {
            throw new IllegalArgumentException("버전 1.0의 로드맵 이름, 주제, 기간(1~52주), 단계(1~20개)를 확인해주세요.");
        }
        Map<String, RoadmapStepDefinition> byKey = new LinkedHashMap<>();
        for (RoadmapStepDefinition step : request.steps()) {
            if (step == null || isBlank(step.key()) || !STEP_KEY.matcher(step.key().trim()).matches()
                    || isBlank(step.title()) || step.title().trim().length() > 120
                    || step.questionTarget() == null || step.questionTarget() < 1 || step.questionTarget() > 20
                    || (step.description() != null && step.description().length() > 1000)
                    || (step.topic() != null && step.topic().length() > 120)) {
                throw new IllegalArgumentException("단계 식별자, 이름, 주제, 목표 문제 수(1~20)를 확인해주세요.");
            }
            String key = step.key().trim();
            if (byKey.put(key, step) != null) {
                throw new IllegalArgumentException("단계 식별자 '" + key + "'가 중복되었습니다.");
            }
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
