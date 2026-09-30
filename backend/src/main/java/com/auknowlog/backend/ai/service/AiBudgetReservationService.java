package com.auknowlog.backend.ai.service;

import com.auknowlog.backend.auth.service.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** Short DB transactions reserve quotas; no row lock is held across an upstream HTTP call. */
@Service
public class AiBudgetReservationService {
    private final JdbcTemplate jdbc;
    private final CurrentUserService users;
    private final TransactionTemplate tx;
    @Value("${auknowlog.ai-policy.daily-token-budget:50000}") private long globalLimit = 50000;
    @Value("${auknowlog.ai-policy.enforce-daily-token-budget:true}") private boolean enforceGlobal = true;

    public AiBudgetReservationService(JdbcTemplate jdbc, CurrentUserService users, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.users = users; tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public JsonNode execute(String operation, String input, int outputLimit, Supplier<JsonNode> call) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Long owner = auth == null ? null : users.currentUserId();
        UUID id = reserve(owner, conservativeTokens(input, outputLimit));
        JsonNode result;
        try { result = call.get(); }
        catch (RuntimeException error) {
            boolean rejected = error instanceof RestClientResponseException response
                    && response.getStatusCode().is4xxClientError() && response.getStatusCode().value() != 408;
            settle(id, null, rejected);
            throw error;
        }
        Long actual = null;
        if (result != null) {
            JsonNode usage = result.path("usage");
            if (usage.path("total_tokens").isNumber()) actual = Math.max(0, usage.path("total_tokens").asLong());
            else if (usage.path("prompt_tokens").isNumber()) actual = Math.max(0, usage.path("prompt_tokens").asLong());
        }
        settle(id, actual, false);
        return result;
    }

    public static long conservativeTokens(String input, int outputLimit) {
        // UTF-8 bytes + request framing allowance, not the previous characters/4 underestimate.
        return (input == null ? 0 : input.getBytes(StandardCharsets.UTF_8).length) + 1024L + Math.max(0, outputLimit);
    }

    public UUID reserve(Long owner, long estimated) {
        if (estimated < 0) throw new IllegalArgumentException("잘못된 AI 예약량입니다.");
        return tx.execute(status -> {
            LocalDate day = LocalDate.now();
            List<String> scopes = scopes(owner);
            List<Map<String, Object>> buckets = new ArrayList<>();
            for (String scope : scopes) {
                ensureBucket(day, scope, owner);
                buckets.add(jdbc.queryForMap("SELECT * FROM ai_budget_bucket WHERE budget_date=? AND scope=? FOR UPDATE", day, scope));
            }
            check(buckets.getFirst(), enforceGlobal ? globalLimit : Long.MAX_VALUE, Integer.MAX_VALUE, estimated);
            if (owner != null) {
                Map<String, Object> limits = jdbc.queryForMap("SELECT ai_daily_token_limit,ai_daily_call_limit FROM app_user WHERE id=?", owner);
                check(buckets.get(1), number(limits, "ai_daily_token_limit"), number(limits, "ai_daily_call_limit"), estimated);
            }
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO ai_budget_reservation(id,owner_id,budget_date,estimated_tokens,status) VALUES (?,?,?,?, 'RESERVED')", id, owner, day, estimated);
            for (String scope : scopes) jdbc.update("UPDATE ai_budget_bucket SET reserved_tokens=reserved_tokens+?,started_calls=started_calls+1 WHERE budget_date=? AND scope=?", estimated, day, scope);
            return id;
        });
    }

    public void settle(UUID id, Long actual, boolean release) {
        tx.executeWithoutResult(status -> {
            Map<String, Object> reservation = jdbc.queryForMap("SELECT * FROM ai_budget_reservation WHERE id=? FOR UPDATE", id);
            if (!"RESERVED".equals(reservation.get("status"))) return;
            Long owner = reservation.get("owner_id") == null ? null : ((Number) reservation.get("owner_id")).longValue();
            long estimated = number(reservation, "estimated_tokens");
            long charged = release ? 0 : actual == null ? estimated : Math.max(0, actual);
            for (String scope : scopes(owner)) {
                jdbc.update("UPDATE ai_budget_bucket SET reserved_tokens=reserved_tokens-?,used_tokens=used_tokens+? WHERE budget_date=? AND scope=?",
                        estimated, charged, reservation.get("budget_date"), scope);
            }
            jdbc.update("UPDATE ai_budget_reservation SET actual_tokens=?,status=?,settled_at=CURRENT_TIMESTAMP WHERE id=?",
                    actual, release ? "RELEASED" : actual == null ? "ESTIMATED" : "SETTLED", id);
        });
    }

    public Map<String, Object> snapshot(Long owner) {
        return tx.execute(status -> {
            LocalDate day = LocalDate.now(); String scope = owner == null ? "GLOBAL" : "USER-" + owner;
            ensureBucket(day, scope, owner);
            Map<String, Object> bucket = jdbc.queryForMap("SELECT * FROM ai_budget_bucket WHERE budget_date=? AND scope=?", day, scope);
            Map<String, Object> limits;
            if (owner == null) limits = Map.of("ai_daily_token_limit", globalLimit, "ai_daily_call_limit", Integer.MAX_VALUE);
            else {
                var rows = jdbc.queryForList("SELECT ai_daily_token_limit,ai_daily_call_limit FROM app_user WHERE id=?",owner);
                if(rows.isEmpty()) throw new NoSuchElementException("사용자를 찾을 수 없습니다.");
                limits = rows.getFirst();
            }
            return Map.of("day", day, "usedTokens", number(bucket, "used_tokens"), "reservedTokens", number(bucket, "reserved_tokens"),
                    "startedCalls", number(bucket, "started_calls"), "tokenLimit", number(limits, "ai_daily_token_limit"),
                    "callLimit", number(limits, "ai_daily_call_limit"));
        });
    }

    private void ensureBucket(LocalDate day, String scope, Long owner) {
        String filter = scope.equals("GLOBAL") ? "" : " AND owner_id=?";
        List<Object> args = new ArrayList<>(List.of(day, scope, day.atStartOfDay(), day.plusDays(1).atStartOfDay()));
        if (!filter.isEmpty()) args.add(owner);
        jdbc.update("INSERT INTO ai_budget_bucket(budget_date,scope,used_tokens,started_calls) SELECT ?,?,coalesce(sum(total_tokens),0),count(*) FROM ai_generation_log WHERE created_at>=? AND created_at<?"
                + filter + " ON CONFLICT (budget_date,scope) DO NOTHING", args.toArray());
    }
    private void check(Map<String, Object> bucket, long tokenLimit, long callLimit, long estimated) {
        long available = tokenLimit - number(bucket,"used_tokens") - number(bucket,"reserved_tokens");
        if (estimated > available || number(bucket,"started_calls") >= callLimit) {
            throw new AiBudgetExceededException();
        }
    }
    private long number(Map<String, Object> map, String key) { return ((Number) map.get(key)).longValue(); }
    private List<String> scopes(Long owner) { return owner == null ? List.of("GLOBAL") : List.of("GLOBAL", "USER-" + owner); }
}
