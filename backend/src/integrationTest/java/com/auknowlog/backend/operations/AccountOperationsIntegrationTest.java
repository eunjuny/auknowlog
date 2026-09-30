package com.auknowlog.backend.operations;

import com.auknowlog.backend.ai.service.*;
import com.auknowlog.backend.auth.service.CurrentUserService;
import com.auknowlog.backend.admin.AdminAuditService;
import com.auknowlog.backend.notification.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class AccountOperationsIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg16-bookworm"))
            .withDatabaseName("operations_test").withUsername("fixture").withPassword("fixture-only");
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager manager;
    CurrentUserService users;
    AiBudgetReservationService budget;
    LearningNotificationService mail;
    JavaMailSender sender;
    List<SimpleMailMessage> messages;
    Long owner, other;

    @BeforeAll static void database() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source); manager = new DataSourceTransactionManager(source);
    }
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE ai_budget_reservation,ai_budget_bucket,admin_audit_log,learning_notification_outbox,user_notification_setting,ai_generation_log,learning_quiz RESTART IDENTITY CASCADE");
        for (String username : List.of("operation-user", "operation-other"))
            jdbc.update("INSERT INTO app_user(keycloak_subject,username) VALUES (?,?) ON CONFLICT(username) DO NOTHING", "fixture-"+username,username);
        owner = jdbc.queryForObject("SELECT id FROM app_user WHERE username='operation-user'",Long.class);
        other = jdbc.queryForObject("SELECT id FROM app_user WHERE username='operation-other'",Long.class);
        jdbc.update("UPDATE app_user SET ai_daily_token_limit=20000,ai_daily_call_limit=100");
        users = mock(CurrentUserService.class); when(users.currentUserId()).thenReturn(owner);
        budget = new AiBudgetReservationService(jdbc,users,manager);
        sender = mock(JavaMailSender.class); messages = new CopyOnWriteArrayList<>();
        doAnswer(call -> { messages.add(new SimpleMailMessage(call.getArgument(0,SimpleMailMessage.class))); return null; })
                .when(sender).send(any(SimpleMailMessage.class));
        mail = new LearningNotificationService(jdbc,users,sender,
                new RemoteAccessMailProperties("smtp.example.invalid",587,"sender@example.invalid","fixture-only",null),manager);
        ReflectionTestUtils.setField(mail,"enabled",true);
    }
    @AfterEach void clearAuth() { SecurityContextHolder.clearContext(); }

    @Test void concurrentReservationsCannotOversubscribeOneUser() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            var ready = new CountDownLatch(8); var start = new CountDownLatch(1);
            List<Future<UUID>> futures = new ArrayList<>();
            for (int i=0;i<8;i++) futures.add(pool.submit(() -> {
                ready.countDown(); start.await();
                try { return budget.reserve(owner,9000); } catch(AiBudgetExceededException denied) { return null; }
            }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            List<UUID> accepted = new ArrayList<>();
            for (var future : futures) { UUID id=future.get(20,TimeUnit.SECONDS); if(id!=null) accepted.add(id); }
            assertThat(accepted).hasSize(2);
            assertThat(budget.snapshot(owner).get("reservedTokens")).isEqualTo(18000L);
            for(UUID id:accepted) { budget.settle(id,100L,false); budget.settle(id,100L,false); }
            assertThat(budget.snapshot(owner).get("usedTokens")).isEqualTo(200L);
            assertThat(budget.snapshot(owner).get("reservedTokens")).isEqualTo(0L);
        } finally { pool.shutdownNow(); }
    }
    @Test void sharedBudgetProtectsAgainstDifferentUsersAndSystemCalls() {
        ReflectionTestUtils.setField(budget,"globalLimit",10000L);
        UUID id=budget.reserve(owner,9000);
        assertThatThrownBy(() -> budget.reserve(other,2000)).isInstanceOf(AiBudgetExceededException.class);
        assertThatThrownBy(() -> budget.reserve(null,2000)).isInstanceOf(AiBudgetExceededException.class);
        budget.settle(id,null,true);
        assertThat(budget.snapshot(owner).get("reservedTokens")).isEqualTo(0L);
        assertThat(budget.snapshot(owner).get("startedCalls")).isEqualTo(1L);
        assertThat(budget.reserve(other,2000)).isNotNull();
    }
    @Test void responseUsageSettlesWhileUnknownErrorsStayConservativelyCharged() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture","unused",List.of()));
        budget.execute("fixture","hello",10,() -> new ObjectMapper().createObjectNode().set("usage",new ObjectMapper().createObjectNode().put("total_tokens",50)));
        assertThat(budget.snapshot(owner).get("usedTokens")).isEqualTo(50L);
        assertThatThrownBy(() -> budget.execute("fixture","hello",10,() -> { throw new IllegalStateException("unknown-upstream-state"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(budget.snapshot(owner).get("reservedTokens")).isEqualTo(0L);
        assertThat(((Number)budget.snapshot(owner).get("usedTokens")).longValue()).isGreaterThan(50L);
        jdbc.update("UPDATE app_user SET ai_daily_call_limit=2 WHERE id=?",owner);
        assertThatThrownBy(() -> budget.reserve(owner,1)).isInstanceOf(AiBudgetExceededException.class);
    }
    @Test void auditContainsOperationAndTargetButNeverRequestContent() {
        new AdminAuditService(jdbc,users).record("GET","/api/admin/users/"+other+"/ai-budget",null,200);
        var row=jdbc.queryForMap("SELECT * FROM admin_audit_log");
        assertThat(row.get("actor_id")).isEqualTo(owner);
        assertThat(row.get("target_user_id")).isEqualTo(other);
        assertThat(row.get("action")).isEqualTo("GET /api/admin/users/"+other+"/ai-budget");
        assertThat(row).doesNotContainKeys("email","token","body");
    }
    @Test void explicitUpstreamRejectionReleasesTokensButStillCountsTheAttempt() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture","unused",List.of()));
        assertThatThrownBy(() -> budget.execute("fixture","sample",10,() -> {
            throw org.springframework.web.client.HttpClientErrorException.create(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"fixture",new org.springframework.http.HttpHeaders(),new byte[0],null);
        })).isInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
        assertThat(budget.snapshot(owner).get("usedTokens")).isEqualTo(0L);
        assertThat(budget.snapshot(owner).get("reservedTokens")).isEqualTo(0L);
        assertThat(budget.snapshot(owner).get("startedCalls")).isEqualTo(1L);
    }
    private void dueReview() {
        Long quiz=jdbc.queryForObject("INSERT INTO learning_quiz(owner_id,topic,title) VALUES (?,'fixture','fixture') RETURNING id",Long.class,owner);
        Long question=jdbc.queryForObject("INSERT INTO learning_question(quiz_id,question_order,question_text,options,correct_answer,explanation) VALUES (?,1,'fixture','[]','A','fixture') RETURNING id",Long.class,quiz);
        jdbc.update("INSERT INTO review_schedule(question_id,next_review_at,status) VALUES (?,?,'PENDING')",question,java.time.LocalDateTime.now().minusDays(1));
    }
    private void verified() {
        mail.save("learner@example.invalid",true,false,8); mail.requestVerification();
        String code=messages.getFirst().getText().split("\\n")[0].substring("인증 코드: ".length());
        mail.verify(code); assertThat(mail.settings().get("verified")).isEqualTo(true);
    }
    @Test void verifiesOwnEmailDeduplicatesReminderAndHidesOtherUsersHistory() {
        dueReview(); mail.save("learner@example.invalid",true,false,8);
        assertThatThrownBy(mail::enqueueMine).isInstanceOf(IllegalArgumentException.class);
        verified(); assertThat(mail.enqueueMine()).isTrue(); assertThat(mail.enqueueMine()).isFalse();
        assertThat(mail.dispatchOne()).isTrue(); assertThat(mail.dispatchOne()).isFalse();
        assertThat(messages).hasSize(2);
        assertThat(messages.getLast().getTo()).containsExactly("learner@example.invalid");
        assertThat(messages.getLast().getText()).contains("오늘 복습할 문제: 1개").doesNotContain("비밀번호");
        assertThat(jdbc.queryForObject("SELECT status FROM learning_notification_outbox",String.class)).isEqualTo("SENT");
        when(users.currentUserId()).thenReturn(other);
        assertThat(mail.history(0).get("total")).isEqualTo(0L);
        assertThatThrownBy(() -> mail.retryMine(1L)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void changingEmailRevokesVerificationAndCancelsPendingJobs() {
        dueReview(); verified(); mail.enqueueMine();
        mail.save("changed@example.invalid",true,false,8);
        assertThat(mail.settings().get("verified")).isEqualTo(false);
        assertThat(jdbc.queryForObject("SELECT status FROM learning_notification_outbox",String.class)).isEqualTo("CANCELLED");
        assertThat(mail.dispatchOne()).isFalse();
        assertThatThrownBy(mail::requestVerification).hasMessageContaining("5분");
    }
    @Test void simultaneousEnqueueAndWorkersProduceOneHealthySmtpSubmission() throws Exception {
        dueReview(); verified();
        var pool=Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> enqueues=new ArrayList<>();
            for(int i=0;i<8;i++) enqueues.add(mail::enqueueMine);
            int queued=0;
            for(var future:pool.invokeAll(enqueues)) if(future.get()) queued++;
            assertThat(queued).isEqualTo(1);
            List<Callable<Boolean>> workers=List.of(mail::dispatchOne,mail::dispatchOne);
            var delivered=pool.invokeAll(workers);
            int claimed=0; for(var future:delivered) if(future.get()) claimed++;
            assertThat(claimed).isEqualTo(1);
            assertThat(messages).hasSize(2); // one verification plus one learning message
        } finally { pool.shutdownNow(); }
    }
    @Test void failedSmtpHasBoundedAutomaticAndManualRetries() {
        dueReview(); verified(); mail.enqueueMine();
        doThrow(new MailSendException("fixture failure with private provider detail")).when(sender).send(any(SimpleMailMessage.class));
        for(int i=0;i<3;i++) { jdbc.update("UPDATE learning_notification_outbox SET available_at=?",java.time.LocalDateTime.now().minusDays(1)); assertThat(mail.dispatchOne()).isTrue(); }
        var row=jdbc.queryForMap("SELECT * FROM learning_notification_outbox");
        assertThat(row.get("status")).isEqualTo("FAILED"); assertThat(row.get("attempts")).isEqualTo(3);
        assertThat(row.get("failure_type")).isEqualTo("SMTP_OR_STORAGE_ERROR");
        mail.retryMine(((Number)row.get("id")).longValue());
        assertThat(jdbc.queryForObject("SELECT status FROM learning_notification_outbox",String.class)).isEqualTo("PENDING");
        jdbc.update("UPDATE learning_notification_outbox SET status='FAILED'");
        assertThatThrownBy(() -> mail.retryMine(((Number)row.get("id")).longValue())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void failedVerificationCodesLockOutAfterTenAttempts() {
        mail.save("learner@example.invalid",true,false,8); mail.requestVerification();
        String correct=messages.getFirst().getText().split("\\n")[0].substring("인증 코드: ".length());
        for(int i=0;i<10;i++) assertThatThrownBy(() -> mail.verify("invalid!")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mail.verify(correct)).isInstanceOf(IllegalArgumentException.class);
        assertThat(mail.settings().get("verified")).isEqualTo(false);
    }
}
