package com.auknowlog.backend.quality.repository;

import com.auknowlog.backend.quality.dto.QualityEvaluationSummary.RunSummary;
import com.auknowlog.backend.quality.dto.QualityReviewQueue.DuplicateReviewItem;
import com.auknowlog.backend.quality.dto.QualityReviewQueue.ObjectiveReviewItem;
import com.auknowlog.backend.quality.dto.QualityRoadmapStepOption;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class QualityEvaluationRepository {

    private final JdbcTemplate jdbcTemplate;

    public QualityEvaluationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long createRun(String evaluationType, String sourceKey, String model, String promptVersion) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO quality_evaluation_run (
                    evaluation_type, source_key, model, prompt_version, status
                ) VALUES (?, ?, ?, ?, 'RUNNING')
                RETURNING id
                """, Long.class, evaluationType, sourceKey, model, promptVersion);
        if (id == null) {
            throw new IllegalStateException("품질 평가 실행 ID를 생성하지 못했습니다.");
        }
        return id;
    }

    public void completeRun(long runId, int candidateCount, int reviewRequiredCount,
                            Long inputTokens, Long outputTokens, Long totalTokens, String model) {
        jdbcTemplate.update("""
                UPDATE quality_evaluation_run
                SET status = 'COMPLETED', candidate_count = ?, review_required_count = ?,
                    input_tokens = ?, output_tokens = ?, total_tokens = ?,
                    model = COALESCE(?, model), completed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, candidateCount, reviewRequiredCount, inputTokens, outputTokens, totalTokens, model, runId);
    }

    public void failRun(long runId, String failureType) {
        jdbcTemplate.update("""
                UPDATE quality_evaluation_run
                SET status = 'FAILED', failure_type = ?, completed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, failureType, runId);
    }

    public List<DuplicateCandidate> findDuplicateCandidates(double candidateFloor, int limit) {
        return jdbcTemplate.query("""
                SELECT first_embedding.question_history_id AS question_a_id,
                       first_question.topic AS question_a_topic,
                       first_question.question_text AS question_a_text,
                       neighbor.question_history_id AS question_b_id,
                       second_question.topic AS question_b_topic,
                       second_question.question_text AS question_b_text,
                       1 - (first_embedding.embedding <=> neighbor.embedding) AS similarity,
                       first_embedding.embedding_model AS embedding_model
                FROM question_embedding first_embedding
                JOIN LATERAL (
                    SELECT candidate.question_history_id, candidate.embedding
                    FROM question_embedding candidate
                    WHERE candidate.question_history_id > first_embedding.question_history_id
                    ORDER BY candidate.embedding <=> first_embedding.embedding
                    LIMIT 5
                ) neighbor ON TRUE
                JOIN question_history first_question ON first_question.id = first_embedding.question_history_id
                JOIN question_history second_question ON second_question.id = neighbor.question_history_id
                WHERE 1 - (first_embedding.embedding <=> neighbor.embedding) >= ?
                ORDER BY ABS((1 - (first_embedding.embedding <=> neighbor.embedding)) - 0.875),
                         first_embedding.question_history_id, neighbor.question_history_id
                LIMIT ?
                """, (resultSet, rowNumber) -> new DuplicateCandidate(
                resultSet.getLong("question_a_id"),
                resultSet.getString("question_a_topic"),
                resultSet.getString("question_a_text"),
                resultSet.getLong("question_b_id"),
                resultSet.getString("question_b_topic"),
                resultSet.getString("question_b_text"),
                resultSet.getDouble("similarity"),
                resultSet.getString("embedding_model")
        ), candidateFloor, limit);
    }

    public long upsertDuplicatePair(long questionAId, long questionBId) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO duplicate_question_pair (question_a_id, question_b_id)
                VALUES (?, ?)
                ON CONFLICT (question_a_id, question_b_id)
                DO UPDATE SET question_a_id = EXCLUDED.question_a_id
                RETURNING id
                """, Long.class, questionAId, questionBId);
        if (id == null) {
            throw new IllegalStateException("중복 평가 문제 쌍을 저장하지 못했습니다.");
        }
        return id;
    }

    public void insertDuplicateResult(long runId, long pairId, double similarity,
                                      String embeddingModel, String systemVerdict) {
        jdbcTemplate.update("""
                INSERT INTO duplicate_evaluation_result (
                    run_id, pair_id, similarity, embedding_model, system_verdict
                ) VALUES (?, ?, ?, ?, ?)
                """, runId, pairId, similarity, embeddingModel, systemVerdict);
    }

    public int reviewDuplicatePair(long pairId, String humanVerdict) {
        return jdbcTemplate.update("""
                UPDATE duplicate_question_pair
                SET human_verdict = ?, reviewed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, humanVerdict, pairId);
    }

    public List<LabeledDuplicateSample> findLatestLabeledDuplicateSamples() {
        return jdbcTemplate.query("""
                SELECT DISTINCT ON (pair.id)
                       pair.id, pair.human_verdict, result.similarity
                FROM duplicate_question_pair pair
                JOIN duplicate_evaluation_result result ON result.pair_id = pair.id
                JOIN quality_evaluation_run run ON run.id = result.run_id
                WHERE pair.human_verdict IN ('DUPLICATE', 'RELATED', 'DISTINCT')
                  AND run.status = 'COMPLETED'
                ORDER BY pair.id, result.created_at DESC, result.id DESC
                """, (resultSet, rowNumber) -> new LabeledDuplicateSample(
                resultSet.getLong("id"),
                resultSet.getString("human_verdict"),
                resultSet.getDouble("similarity")
        ));
    }

    public List<DuplicateReviewItem> findDuplicateReviewQueue(int limit) {
        return jdbcTemplate.query("""
                WITH latest_result AS (
                    SELECT DISTINCT ON (pair_id)
                           pair_id, similarity, system_verdict
                    FROM duplicate_evaluation_result
                    ORDER BY pair_id, created_at DESC, id DESC
                )
                SELECT
                       pair.id AS pair_id,
                       question_a.id AS question_a_id,
                       question_a.topic AS question_a_topic,
                       question_a.question_text AS question_a_text,
                       question_b.id AS question_b_id,
                       question_b.topic AS question_b_topic,
                       question_b.question_text AS question_b_text,
                       result.similarity,
                       result.system_verdict,
                       pair.human_verdict
                FROM duplicate_question_pair pair
                JOIN question_history question_a ON question_a.id = pair.question_a_id
                JOIN question_history question_b ON question_b.id = pair.question_b_id
                JOIN latest_result result ON result.pair_id = pair.id
                WHERE pair.human_verdict IS NULL
                  AND result.system_verdict = 'REVIEW_REQUIRED'
                ORDER BY result.similarity DESC, pair.id
                LIMIT ?
                """, (resultSet, rowNumber) -> new DuplicateReviewItem(
                resultSet.getLong("pair_id"),
                resultSet.getLong("question_a_id"),
                resultSet.getString("question_a_topic"),
                resultSet.getString("question_a_text"),
                resultSet.getLong("question_b_id"),
                resultSet.getString("question_b_topic"),
                resultSet.getString("question_b_text"),
                resultSet.getDouble("similarity"),
                resultSet.getString("system_verdict"),
                resultSet.getString("human_verdict")
        ), limit);
    }

    public DuplicateCounts duplicateCounts() {
        return jdbcTemplate.queryForObject("""
                WITH latest_result AS (
                    SELECT DISTINCT ON (pair_id)
                           pair_id, system_verdict
                    FROM duplicate_evaluation_result
                    ORDER BY pair_id, created_at DESC, id DESC
                )
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE human_verdict IS NOT NULL) AS reviewed,
                       COUNT(*) FILTER (
                           WHERE human_verdict IS NULL AND latest_result.system_verdict = 'REVIEW_REQUIRED'
                       ) AS pending
                FROM duplicate_question_pair pair
                LEFT JOIN latest_result ON latest_result.pair_id = pair.id
                """, (resultSet, rowNumber) -> new DuplicateCounts(
                resultSet.getLong("total"),
                resultSet.getLong("reviewed"),
                resultSet.getLong("pending")
        ));
    }

    public StepEvaluationInput findStepEvaluationInput(long roadmapStepId) {
        StepRow step = jdbcTemplate.query("""
                SELECT step.id, step.topic, step.title, roadmap.title AS roadmap_title
                FROM learning_roadmap_step step
                JOIN learning_roadmap roadmap ON roadmap.id = step.roadmap_id
                WHERE step.id = ?
                """, (resultSet, rowNumber) -> new StepRow(
                resultSet.getLong("id"), resultSet.getString("topic"),
                resultSet.getString("title"), resultSet.getString("roadmap_title")
        ), roadmapStepId).stream().findFirst().orElseThrow(
                () -> new IllegalArgumentException("평가할 로드맵 학습 단계를 찾을 수 없습니다."));

        List<ObjectiveInput> objectives = jdbcTemplate.query("""
                SELECT id, objective_key, title, description, importance
                FROM learning_objective
                WHERE roadmap_step_id = ?
                ORDER BY objective_order
                """, (resultSet, rowNumber) -> new ObjectiveInput(
                resultSet.getLong("id"), resultSet.getString("objective_key"),
                resultSet.getString("title"), resultSet.getString("description"),
                resultSet.getString("importance")
        ), roadmapStepId);

        List<QuestionInput> questions = jdbcTemplate.query("""
                SELECT question.id, question.question_text, question.learning_objective_id
                FROM learning_question question
                JOIN learning_quiz quiz ON quiz.id = question.quiz_id
                WHERE quiz.roadmap_step_id = ?
                ORDER BY quiz.created_at DESC, question.question_order
                LIMIT 30
                """, (resultSet, rowNumber) -> new QuestionInput(
                resultSet.getLong("id"), resultSet.getString("question_text"),
                resultSet.getObject("learning_objective_id", Long.class)
        ), roadmapStepId);

        if (objectives.isEmpty()) {
            throw new IllegalArgumentException("이 학습 단계에는 평가할 학습 목표가 없습니다.");
        }
        return new StepEvaluationInput(step.id(), step.roadmapTitle(), step.title(), step.topic(), objectives, questions);
    }

    public List<QualityRoadmapStepOption> findRoadmapStepOptions() {
        return jdbcTemplate.query("""
                SELECT step.id, roadmap.title AS roadmap_title, step.title, step.topic,
                       COUNT(DISTINCT objective.id) AS objective_count,
                       COUNT(DISTINCT question.id) AS question_count
                FROM learning_roadmap_step step
                JOIN learning_roadmap roadmap ON roadmap.id = step.roadmap_id
                LEFT JOIN learning_objective objective ON objective.roadmap_step_id = step.id
                LEFT JOIN learning_quiz quiz ON quiz.roadmap_step_id = step.id
                LEFT JOIN learning_question question ON question.quiz_id = quiz.id
                GROUP BY step.id, roadmap.title, step.title, step.topic, roadmap.created_at, step.step_order
                HAVING COUNT(DISTINCT objective.id) > 0
                ORDER BY roadmap.created_at DESC, step.step_order
                LIMIT 100
                """, (resultSet, rowNumber) -> new QualityRoadmapStepOption(
                resultSet.getLong("id"), resultSet.getString("roadmap_title"),
                resultSet.getString("title"), resultSet.getString("topic"),
                resultSet.getLong("objective_count"), resultSet.getLong("question_count")
        ));
    }

    public void insertObjectiveCase(long runId, String caseType, String topic,
                                    String referenceKey, String referenceTitle, String importance,
                                    Long learningObjectiveId, Long learningQuestionId,
                                    String aiVerdict, double confidence, String rationale,
                                    String reviewStatus) {
        jdbcTemplate.update("""
                INSERT INTO objective_evaluation_case (
                    run_id, case_type, topic, reference_key, reference_title, importance,
                    learning_objective_id, learning_question_id, ai_verdict, ai_confidence,
                    ai_rationale, review_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, runId, caseType, topic, referenceKey, referenceTitle, importance,
                learningObjectiveId, learningQuestionId, aiVerdict, confidence, rationale, reviewStatus);
    }

    public int reviewObjectiveCase(long caseId, String humanVerdict) {
        return jdbcTemplate.update("""
                UPDATE objective_evaluation_case
                SET human_verdict = ?, review_status = 'REVIEWED', reviewed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, humanVerdict, caseId);
    }

    public List<ObjectiveReviewItem> findObjectiveReviewQueue(int limit) {
        return jdbcTemplate.query("""
                SELECT evaluation.id, evaluation.case_type, evaluation.topic,
                       evaluation.reference_title, evaluation.importance,
                       evaluation.learning_objective_id, objective.title AS objective_title,
                       evaluation.learning_question_id, question.question_text,
                       evaluation.ai_verdict, evaluation.ai_confidence,
                       evaluation.ai_rationale, evaluation.human_verdict
                FROM objective_evaluation_case evaluation
                LEFT JOIN learning_objective objective ON objective.id = evaluation.learning_objective_id
                LEFT JOIN learning_question question ON question.id = evaluation.learning_question_id
                WHERE evaluation.review_status = 'REVIEW_REQUIRED'
                ORDER BY evaluation.created_at DESC, evaluation.id DESC
                LIMIT ?
                """, (resultSet, rowNumber) -> new ObjectiveReviewItem(
                resultSet.getLong("id"), resultSet.getString("case_type"),
                resultSet.getString("topic"), resultSet.getString("reference_title"),
                resultSet.getString("importance"),
                resultSet.getObject("learning_objective_id", Long.class),
                resultSet.getString("objective_title"),
                resultSet.getObject("learning_question_id", Long.class),
                resultSet.getString("question_text"),
                resultSet.getString("ai_verdict"), resultSet.getDouble("ai_confidence"),
                resultSet.getString("ai_rationale"), resultSet.getString("human_verdict")
        ), limit);
    }

    public ObjectiveCounts objectiveCounts() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE review_status = 'AUTO_ACCEPTED') AS automatic_accepted,
                       COUNT(*) FILTER (WHERE review_status = 'REVIEW_REQUIRED') AS pending,
                       COUNT(*) FILTER (WHERE review_status = 'REVIEWED') AS reviewed
                FROM objective_evaluation_case
                """, (resultSet, rowNumber) -> new ObjectiveCounts(
                resultSet.getLong("total"), resultSet.getLong("automatic_accepted"),
                resultSet.getLong("pending"), resultSet.getLong("reviewed")
        ));
    }

    public List<ObjectiveOutcome> objectiveOutcomes() {
        return jdbcTemplate.query("""
                SELECT case_type, ai_verdict, human_verdict
                FROM objective_evaluation_case
                """, (resultSet, rowNumber) -> new ObjectiveOutcome(
                resultSet.getString("case_type"), resultSet.getString("ai_verdict"),
                resultSet.getString("human_verdict")
        ));
    }

    public List<RunSummary> recentRuns() {
        return jdbcTemplate.query("""
                SELECT id, evaluation_type, source_key, model, prompt_version, status,
                       candidate_count, review_required_count, total_tokens, created_at
                FROM quality_evaluation_run
                ORDER BY created_at DESC, id DESC
                LIMIT 10
                """, (resultSet, rowNumber) -> new RunSummary(
                resultSet.getLong("id"), resultSet.getString("evaluation_type"),
                resultSet.getString("source_key"), resultSet.getString("model"),
                resultSet.getString("prompt_version"), resultSet.getString("status"),
                resultSet.getInt("candidate_count"), resultSet.getInt("review_required_count"),
                resultSet.getObject("total_tokens", Long.class),
                resultSet.getTimestamp("created_at").toLocalDateTime()
        ));
    }

    public record DuplicateCandidate(long questionAId, String questionATopic, String questionAText,
                                     long questionBId, String questionBTopic, String questionBText,
                                     double similarity, String embeddingModel) {
    }

    public record LabeledDuplicateSample(long pairId, String humanVerdict, double similarity) {
    }

    public record DuplicateCounts(long total, long reviewed, long pending) {
    }

    public record ObjectiveCounts(long total, long automaticAccepted, long pending, long reviewed) {
    }

    public record ObjectiveOutcome(String caseType, String aiVerdict, String humanVerdict) {
    }

    public record StepEvaluationInput(long stepId, String roadmapTitle, String stepTitle, String topic,
                                      List<ObjectiveInput> objectives, List<QuestionInput> questions) {
    }

    public record ObjectiveInput(long id, String key, String title, String description, String importance) {
    }

    public record QuestionInput(long id, String text, Long objectiveId) {
    }

    private record StepRow(long id, String topic, String title, String roadmapTitle) {
    }
}
