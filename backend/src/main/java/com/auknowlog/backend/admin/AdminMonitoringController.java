package com.auknowlog.backend.admin;

import com.auknowlog.backend.ai.service.AiUsagePolicyService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Read-only cross-user queries. Authorization is enforced at the /api/admin boundary. */
@RestController
@RequestMapping("/api/admin")
@Transactional(readOnly = true)
public class AdminMonitoringController {
    private final JdbcTemplate jdbc;
    private final AiUsagePolicyService policy;

    public AdminMonitoringController(JdbcTemplate jdbc, AiUsagePolicyService policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return Map.of("learning", jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM app_user) AS users,
                       (SELECT count(*) FROM learning_attempt) AS attempts,
                       (SELECT count(*) FROM learning_roadmap) AS roadmaps,
                       (SELECT count(*) FROM review_schedule WHERE status = 'PENDING') AS "pendingReviews"
                """), "ai", jdbc.queryForMap("""
                SELECT count(*) AS calls, coalesce(sum(total_tokens),0) AS tokens,
                       count(*) FILTER (WHERE status <> 'SUCCESS') AS failures,
                       coalesce(round(avg(latency_ms)),0) AS "averageLatencyMs",
                       count(*) FILTER (WHERE owner_id IS NULL) AS "unattributedCalls"
                FROM ai_generation_log
                """), "budget", policy.snapshot(), "dailyAi", jdbc.queryForList("""
                SELECT cast(created_at AS date) AS day, count(*) AS calls,
                       coalesce(sum(total_tokens),0) AS tokens,
                       count(*) FILTER (WHERE status <> 'SUCCESS') AS failures
                FROM ai_generation_log WHERE created_at >= CURRENT_DATE - INTERVAL '13 days'
                GROUP BY cast(created_at AS date) ORDER BY day
                """));
    }

    @GetMapping("/users")
    public Map<String, Object> users(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        int limit = limit(size), offset = offset(page, limit);
        return Map.of("total", jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class),
                "items", jdbc.queryForList("""
                SELECT u.id, u.username, u.display_name AS "displayName", u.active,
                       (SELECT count(*) FROM learning_attempt a JOIN learning_quiz q ON q.id=a.quiz_id
                        WHERE q.owner_id=u.id) AS attempts,
                       (SELECT count(*) FROM learning_roadmap r WHERE r.owner_id=u.id) AS roadmaps,
                       (SELECT coalesce(sum(total_tokens),0) FROM ai_generation_log l WHERE l.owner_id=u.id) AS tokens
                FROM app_user u ORDER BY u.id LIMIT ? OFFSET ?
                """, limit, offset));
    }

    @GetMapping("/records/{kind}")
    public Map<String, Object> records(@PathVariable String kind,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        // Only fixed SQL fragments are selected; user input never becomes SQL text.
        String from, columns, owner, order;
        switch (kind) {
            case "attempts" -> {
                from = "learning_attempt a JOIN learning_quiz q ON q.id=a.quiz_id JOIN app_user u ON u.id=q.owner_id";
                columns = "a.id, u.username, q.title, q.topic, a.total_questions AS \"totalQuestions\", a.correct_answers AS \"correctAnswers\", a.submitted_at AS time";
                owner = "q.owner_id"; order = "a.submitted_at";
            }
            case "roadmaps" -> {
                from = "learning_roadmap r JOIN app_user u ON u.id=r.owner_id";
                columns = "r.id, u.username, r.title, r.topic, r.status, r.created_at AS time";
                owner = "r.owner_id"; order = "r.created_at";
            }
            case "reviews" -> {
                from = "review_schedule r JOIN learning_question k ON k.id=r.question_id JOIN learning_quiz q ON q.id=k.quiz_id JOIN app_user u ON u.id=q.owner_id";
                columns = "r.id, u.username, q.topic, k.question_text AS title, r.status, r.next_review_at AS time, r.lapse_count AS \"lapseCount\"";
                owner = "q.owner_id"; order = "r.next_review_at";
            }
            case "ai" -> {
                from = "ai_generation_log l LEFT JOIN app_user u ON u.id=l.owner_id";
                columns = "l.id, coalesce(u.username,'시스템 / 기존 미귀속') AS username, l.operation, l.model, l.status, l.input_tokens AS \"inputTokens\", l.output_tokens AS \"outputTokens\", l.total_tokens AS tokens, l.latency_ms AS \"latencyMs\", l.failure_type AS \"failureType\", l.created_at AS time";
                owner = "l.owner_id"; order = "l.created_at";
            }
            case "review-attempts" -> {
                from = "review_attempt a JOIN review_schedule r ON r.id=a.review_schedule_id JOIN learning_question k ON k.id=r.question_id JOIN learning_quiz q ON q.id=k.quiz_id JOIN app_user u ON u.id=q.owner_id";
                columns = "a.id, u.username, q.topic, k.question_text AS title, a.is_correct AS correct, a.status_after AS status, a.reviewed_at AS time, a.interval_after AS \"intervalDays\"";
                owner = "q.owner_id"; order = "a.reviewed_at";
            }
            case "daily" -> {
                from = "daily_learning_progress p JOIN daily_learning d ON d.id=p.daily_learning_id JOIN app_user u ON u.id=p.owner_id";
                columns = "p.id, u.username, d.article_title AS title, p.status, p.updated_at AS time";
                owner = "p.owner_id"; order = "p.updated_at";
            }
            case "audits" -> {
                from = "admin_audit_log a LEFT JOIN app_user u ON u.id=a.actor_id";
                columns = "a.id, u.username, a.action AS title, a.target_user_id AS \"targetUserId\", a.response_status AS status, a.created_at AS time";
                owner = "a.target_user_id"; order = "a.created_at";
            }
            case "notifications" -> {
                from = "learning_notification_outbox n JOIN app_user u ON u.id=n.owner_id";
                columns = "n.id, u.username, n.status, n.attempts, n.failure_type AS \"failureType\", n.created_at AS time";
                owner = "n.owner_id"; order = "n.created_at";
            }
            default -> throw new NoSuchElementException("지원하지 않는 관리자 조회입니다.");
        }
        int limit = limit(size), offset = offset(page, limit);
        String where = userId == null ? "" : " WHERE " + owner + "=?";
        Object[] countArgs = userId == null ? new Object[]{} : new Object[]{userId};
        Object[] args = userId == null ? new Object[]{limit, offset} : new Object[]{userId, limit, offset};
        return Map.of("total", jdbc.queryForObject("SELECT count(*) FROM " + from + where, Long.class, countArgs),
                "items", jdbc.queryForList("SELECT " + columns + " FROM " + from + where + " ORDER BY " + order
                        + " DESC LIMIT ? OFFSET ?", args));
    }

    @GetMapping("/attempts/{id}")
    public Map<String, Object> attempt(@PathVariable Long id) {
        List<Map<String, Object>> attempt = jdbc.queryForList("""
                SELECT a.id, u.username, q.title, q.topic, a.submitted_at AS time
                FROM learning_attempt a JOIN learning_quiz q ON q.id=a.quiz_id
                JOIN app_user u ON u.id=q.owner_id WHERE a.id=?
                """, id);
        if (attempt.isEmpty()) throw new NoSuchElementException("풀이 기록을 찾을 수 없습니다.");
        return Map.of("attempt", attempt.getFirst(), "questions", jdbc.queryForList("""
                SELECT k.question_text AS question, k.options, k.correct_answer AS "correctAnswer",
                       k.explanation, k.option_explanations AS "optionExplanations",
                       a.selected_answer AS "selectedAnswer", a.is_correct AS correct
                FROM learning_attempt_answer a JOIN learning_question k ON k.id=a.question_id
                WHERE a.attempt_id=? ORDER BY k.question_order
                """, id));
    }

    private int limit(int size) { return Math.min(100, Math.max(1, size)); }
    private int offset(int page, int size) { return Math.min(100000, Math.max(0, page)) * size; }
}
