package com.auknowlog.backend.admin;

import com.auknowlog.backend.auth.service.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {
    private final JdbcTemplate jdbc;
    private final CurrentUserService users;
    public AdminAuditService(JdbcTemplate jdbc, CurrentUserService users) { this.jdbc = jdbc; this.users = users; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String method, String path, String userId, int status) {
        Long target = null;
        if (userId == null && path.matches("/api/admin/users/[0-9]{1,18}/ai-budget")) userId = path.split("/")[4];
        if (userId != null && userId.matches("[0-9]{1,18}")) {
            var ids = jdbc.queryForList("SELECT id FROM app_user WHERE id=?", Long.class, Long.parseLong(userId));
            if (!ids.isEmpty()) target = ids.getFirst();
        } else if (path.matches("/api/admin/attempts/[0-9]{1,18}")) {
            var ids = jdbc.queryForList("SELECT q.owner_id FROM learning_attempt a JOIN learning_quiz q ON q.id=a.quiz_id WHERE a.id=?",
                    Long.class, Long.parseLong(path.substring(path.lastIndexOf('/') + 1)));
            if (!ids.isEmpty()) target = ids.getFirst();
        }
        // Store only normalized operation names/IDs, never arbitrary request text or query strings.
        String safePath = path.matches("/api/admin/(summary|users|ai-budget|records/(attempts|roadmaps|reviews|review-attempts|daily|ai|audits|notifications)|attempts/[0-9]{1,18}|users/[0-9]{1,18}/ai-budget)")
                ? path : "/api/admin/unmatched";
        String safeMethod = method.matches("GET|PUT|POST|DELETE|PATCH|HEAD|OPTIONS") ? method : "OTHER";
        jdbc.update("INSERT INTO admin_audit_log(actor_id,target_user_id,action,response_status,created_at) VALUES (?,?,?,?,?)",
                users.currentUserId(), target, safeMethod + " " + safePath, status,
                java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));
    }
}
