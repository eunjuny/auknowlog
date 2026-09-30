package com.auknowlog.backend.notification;

import com.auknowlog.backend.auth.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

/** Own verified recipient only; no remote-access credentials are included in learning mail. */
@Service
public class LearningNotificationService {
    private final JdbcTemplate jdbc;
    private final CurrentUserService users;
    private final JavaMailSender sender;
    private final RemoteAccessMailProperties smtp;
    private final TransactionTemplate tx;
    @Value("${auknowlog.learning-mail.enabled:false}") private boolean enabled;

    public LearningNotificationService(JdbcTemplate jdbc, CurrentUserService users, JavaMailSender sender,
            RemoteAccessMailProperties smtp, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.users = users; this.sender = sender; this.smtp = smtp;
        tx = new TransactionTemplate(manager); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Seoul")); }
    private boolean configured() { return !smtp.username().isBlank() && !smtp.appPassword().isBlank(); }
    private void ensure(Long owner) {
        jdbc.update("INSERT INTO user_notification_setting(owner_id) VALUES (?) ON CONFLICT (owner_id) DO NOTHING", owner);
    }
    public Map<String,Object> settings() {
        Long owner = users.currentUserId(); ensure(owner);
        Map<String,Object> setting = jdbc.queryForMap("SELECT email,verified,review_enabled AS \"reviewEnabled\",daily_enabled AS \"dailyEnabled\",send_hour AS \"sendHour\" FROM user_notification_setting WHERE owner_id=?", owner);
        setting.put("deliveryEnabled", enabled); setting.put("smtpConfigured", configured());
        return setting;
    }
    public Map<String,Object> save(String email, boolean review, boolean daily, int hour) {
        Long owner = users.currentUserId();
        String normalized = email == null || email.isBlank() ? null : email.trim();
        if (normalized != null && (normalized.length() > 254 || !normalized.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")))
            throw new IllegalArgumentException("유효한 이메일 주소를 입력해주세요.");
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("발송 시각은 0~23시입니다.");
        tx.executeWithoutResult(status -> {
            ensure(owner);
            jdbc.update("""
                    UPDATE user_notification_setting SET
                      verified=CASE WHEN email IS NOT DISTINCT FROM ? THEN verified ELSE FALSE END,
                      email=?,review_enabled=?,daily_enabled=?,send_hour=?,version=version+1,
                      verification_hash=NULL,verification_expires_at=NULL,updated_at=? WHERE owner_id=?
                    """, normalized, normalized, review, daily, hour, now(), owner);
            jdbc.update("UPDATE learning_notification_outbox SET status='CANCELLED' WHERE owner_id=? AND status IN ('PENDING','FAILED')", owner);
        });
        return settings();
    }

    /** Explicit user action sends one verification email. The code is never stored or returned in plaintext. */
    public void requestVerification() {
        if (!enabled || !configured()) throw new IllegalArgumentException("학습 메일 발송이 비활성화되어 있습니다. 관리자에게 SMTP 및 발송 설정을 문의해주세요.");
        Long owner = users.currentUserId();
        String code = String.format(Locale.ROOT, "%08d", new SecureRandom().nextInt(100000000));
        LocalDateTime time = now();
        String recipient = tx.execute(status -> {
            ensure(owner);
            var setting = jdbc.queryForMap("SELECT * FROM user_notification_setting WHERE owner_id=? FOR UPDATE", owner);
            String email = (String) setting.get("email");
            if (email == null) throw new IllegalArgumentException("이메일을 먼저 저장해주세요.");
            java.sql.Timestamp last = (java.sql.Timestamp) setting.get("verification_requested_at");
            if (last != null && last.toLocalDateTime().isAfter(time.minusMinutes(5)))
                throw new IllegalArgumentException("인증 메일은 5분 뒤 다시 요청할 수 있습니다.");
            boolean sameDay = setting.get("verification_day") != null
                    && ((java.sql.Date) setting.get("verification_day")).toLocalDate().equals(time.toLocalDate());
            int count = sameDay ? ((Number) setting.get("verification_count")).intValue() : 0;
            if (count >= 3) throw new IllegalArgumentException("인증 메일은 하루 최대 3회입니다.");
            jdbc.update("""
                    UPDATE user_notification_setting SET verification_hash=?,verification_expires_at=?,
                     verification_requested_at=?,verification_attempts=0,verification_day=?,verification_count=? WHERE owner_id=?
                    """, hash(code), time.plusMinutes(30), time, time.toLocalDate(), count+1, owner);
            return email;
        });
        try { send(recipient, "[Auknowlog] 학습 알림 이메일 인증", "인증 코드: " + code + "\n30분 안에 로그인한 앱의 계정·알림 화면에서 입력해주세요.\n요청하지 않았다면 무시해주세요."); }
        catch (RuntimeException error) {
            jdbc.update("UPDATE user_notification_setting SET verification_hash=NULL WHERE owner_id=? AND verification_hash=?", owner, hash(code));
            throw new IllegalArgumentException("인증 메일 발송에 실패했습니다. SMTP 설정을 확인해주세요.");
        }
    }
    public void verify(String code) {
        Long owner = users.currentUserId();
        // Increment attempts in a committed transaction even when the subsequent validation fails.
        Map<String,Object> setting = tx.execute(status -> {
            ensure(owner);
            var row = jdbc.queryForMap("SELECT * FROM user_notification_setting WHERE owner_id=? FOR UPDATE", owner);
            jdbc.update("UPDATE user_notification_setting SET verification_attempts=verification_attempts+1 WHERE owner_id=?", owner);
            return row;
        });
        java.sql.Timestamp expiry = (java.sql.Timestamp) setting.get("verification_expires_at");
        if (((Number) setting.get("verification_attempts")).intValue() >= 10 || expiry == null
                || !expiry.toLocalDateTime().isAfter(now()) || code == null
                || !code.matches("[0-9]{8}") || !hash(code).equals(setting.get("verification_hash")))
            throw new IllegalArgumentException("인증 코드가 틀렸거나 만료되었습니다.");
        if (jdbc.update("UPDATE user_notification_setting SET verified=TRUE,verification_hash=NULL,verification_expires_at=NULL WHERE owner_id=? AND verification_hash=? AND verification_attempts<=10", owner, hash(code)) != 1)
            throw new IllegalArgumentException("인증 상태가 변경되었습니다. 다시 요청해주세요.");
    }
    public Map<String,Object> history(int page) {
        Long owner = users.currentUserId();
        return Map.of("items", jdbc.queryForList("SELECT id,status,attempts,manual_retries AS \"manualRetries\",failure_type AS \"failureType\",created_at AS \"createdAt\",sent_at AS \"sentAt\" FROM learning_notification_outbox WHERE owner_id=? ORDER BY id DESC LIMIT 20 OFFSET ?", owner, Math.min(100000,Math.max(0,page))*20),
                "total", jdbc.queryForObject("SELECT count(*) FROM learning_notification_outbox WHERE owner_id=?", Long.class, owner));
    }
    public boolean enqueueMine() {
        return enqueue(users.currentUserId(), false);
    }
    public boolean enqueue(Long owner, boolean scheduled) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            ensure(owner);
            var setting = jdbc.queryForMap("SELECT * FROM user_notification_setting WHERE owner_id=? FOR UPDATE", owner);
            if (!Boolean.TRUE.equals(setting.get("verified"))) {
                if (scheduled) return false;
                throw new IllegalArgumentException("먼저 이메일을 인증해주세요.");
            }
            boolean review = Boolean.TRUE.equals(setting.get("review_enabled")), daily = Boolean.TRUE.equals(setting.get("daily_enabled"));
            if (!review && !daily) {
                if (scheduled) return false;
                throw new IllegalArgumentException("수신할 학습 알림을 선택해주세요.");
            }
            if (scheduled && ((Number)setting.get("send_hour")).intValue() != now().getHour()) return false;
            if (summary(owner, review, daily).isEmpty()) return false;
            return jdbc.update("INSERT INTO learning_notification_outbox(owner_id,settings_version,dedup_key,available_at,created_at) VALUES (?,?,?,?,?) ON CONFLICT(dedup_key) DO NOTHING",
                    owner, setting.get("version"), "LEARNING:"+owner+":"+now().toLocalDate(), now(), now()) == 1;
        }));
    }
    public void retry(Long id, Long owner) {
        if (jdbc.update("""
                UPDATE learning_notification_outbox n SET status='PENDING',attempts=0,manual_retries=manual_retries+1,available_at=?,failure_type=NULL
                WHERE id=? AND owner_id=? AND status='FAILED' AND manual_retries<1
                 AND EXISTS (SELECT 1 FROM user_notification_setting s WHERE s.owner_id=n.owner_id AND s.version=n.settings_version AND s.verified=TRUE AND (s.review_enabled=TRUE OR s.daily_enabled=TRUE))
                """, now(), id, owner) != 1) throw new IllegalArgumentException("다시 보낼 수 없는 알림입니다. 실패 이력과 이메일 설정을 확인해주세요.");
    }
    public void retryMine(Long id) { retry(id, users.currentUserId()); }

    @Scheduled(fixedDelayString = "${auknowlog.learning-mail.poll-ms:60000}")
    public void tick() {
        if (!enabled || !configured()) return;
        // Bounded batch; each user is locked independently and delivery is handled separately.
        var ids = jdbc.queryForList("""
                SELECT s.owner_id FROM user_notification_setting s
                WHERE s.verified=TRUE AND (s.review_enabled=TRUE OR s.daily_enabled=TRUE) AND s.send_hour=?
                 AND NOT EXISTS (SELECT 1 FROM learning_notification_outbox n WHERE n.dedup_key='LEARNING:' || s.owner_id || ':' || ?)
                ORDER BY s.owner_id LIMIT 100
                """, Long.class, now().getHour(), now().toLocalDate().toString());
        for (Long owner : ids.stream().limit(100).toList()) {
            try { enqueue(owner, true); } catch (RuntimeException ignored) { /* next poll retries; no PII logs */ }
        }
        for (int i=0; i<20; i++) if (!dispatchOne()) break;
    }
    public boolean dispatchOne() {
        if (!enabled || !configured()) return false;
        Map<String,Object> job = tx.execute(status -> {
            // SMTP timeouts are 10s; a 5-minute lease handles process interruption conservatively.
            jdbc.update("UPDATE learning_notification_outbox SET status=CASE WHEN attempts>=3 THEN 'FAILED' ELSE 'PENDING' END,failure_type='LEASE_EXPIRED',available_at=? WHERE status='SENDING' AND claimed_at<?", now(), now().minusMinutes(5));
            var rows = jdbc.queryForList("SELECT * FROM learning_notification_outbox WHERE status='PENDING' AND available_at<=? ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1", now());
            if (rows.isEmpty()) return null;
            var row = rows.getFirst();
            jdbc.update("UPDATE learning_notification_outbox SET status='SENDING',attempts=attempts+1,claimed_at=? WHERE id=?", now(), row.get("id"));
            return row;
        });
        if (job == null) return false;
        Long owner = ((Number)job.get("owner_id")).longValue();
        long id = ((Number)job.get("id")).longValue();
        int attempts = ((Number)job.get("attempts")).intValue()+1;
        try {
            var setting = jdbc.queryForMap("SELECT * FROM user_notification_setting WHERE owner_id=?", owner);
            boolean review = Boolean.TRUE.equals(setting.get("review_enabled")), daily = Boolean.TRUE.equals(setting.get("daily_enabled"));
            if (!Boolean.TRUE.equals(setting.get("verified")) || !Objects.equals(setting.get("version"),job.get("settings_version")) || (!review && !daily)) {
                jdbc.update("UPDATE learning_notification_outbox SET status='CANCELLED' WHERE id=?", id); return true;
            }
            String content = summary(owner, review, daily);
            if (content.isEmpty()) { jdbc.update("UPDATE learning_notification_outbox SET status='CANCELLED' WHERE id=?", id); return true; }
            send((String)setting.get("email"), "[Auknowlog] 오늘의 학습 알림", content + "\n\n로그인한 앱에서 학습을 진행해주세요. 수신 설정은 계정·알림 메뉴에서 변경할 수 있습니다.");
            jdbc.update("UPDATE learning_notification_outbox SET status='SENT',sent_at=?,failure_type=NULL WHERE id=?", now(), id);
        } catch (RuntimeException ignored) {
            // Never persist provider exceptions: they may contain addresses or SMTP credentials.
            jdbc.update("UPDATE learning_notification_outbox SET status=?,failure_type='SMTP_OR_STORAGE_ERROR',available_at=? WHERE id=?",
                    attempts>=3 ? "FAILED" : "PENDING", now().plusMinutes(attempts==1 ? 1 : 5), id);
        }
        return true;
    }
    private String summary(Long owner, boolean review, boolean daily) {
        StringBuilder body = new StringBuilder();
        if (review) {
            Long count = jdbc.queryForObject("SELECT count(*) FROM review_schedule r JOIN learning_question k ON k.id=r.question_id JOIN learning_quiz q ON q.id=k.quiz_id WHERE q.owner_id=? AND r.status='PENDING' AND r.next_review_at<=?", Long.class, owner, now());
            if (count != null && count>0) body.append("오늘 복습할 문제: ").append(count).append("개\n");
        }
        if (daily) {
            var titles = jdbc.queryForList("SELECT article_title FROM daily_learning d WHERE learning_date=? AND NOT EXISTS (SELECT 1 FROM daily_learning_progress p WHERE p.daily_learning_id=d.id AND p.owner_id=? AND p.status='COMPLETED')", String.class, now().toLocalDate(), owner);
            if (!titles.isEmpty()) body.append("오늘의 데일리 학습: ").append(titles.getFirst()).append("\n");
        }
        return body.toString();
    }
    private void send(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(smtp.username()); message.setTo(recipient); message.setSubject(subject); message.setText(body);
        sender.send(message);
    }
    private String hash(String code) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
