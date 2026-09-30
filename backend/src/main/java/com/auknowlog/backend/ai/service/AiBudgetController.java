package com.auknowlog.backend.ai.service;

import com.auknowlog.backend.auth.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class AiBudgetController {
    private final AiBudgetReservationService budgets;
    private final CurrentUserService users;
    private final JdbcTemplate jdbc;
    public AiBudgetController(AiBudgetReservationService budgets, CurrentUserService users, JdbcTemplate jdbc) {
        this.budgets = budgets; this.users = users; this.jdbc = jdbc;
    }
    @GetMapping("/api/account/ai-budget") public Map<String,Object> mine() { return budgets.snapshot(users.currentUserId()); }
    @GetMapping("/api/admin/ai-budget") public Map<String,Object> global() { return budgets.snapshot(null); }
    @GetMapping("/api/admin/users/{id}/ai-budget") public Map<String,Object> user(@PathVariable Long id) { return budgets.snapshot(id); }
    public record Limits(@Min(0) @Max(1000000) long tokenLimit, @Min(0) @Max(1000) int callLimit) {}
    @PutMapping("/api/admin/users/{id}/ai-budget") public Map<String,Object> update(@PathVariable Long id, @Valid @RequestBody Limits limits) {
        if (jdbc.update("UPDATE app_user SET ai_daily_token_limit=?,ai_daily_call_limit=? WHERE id=?", limits.tokenLimit(), limits.callLimit(), id) != 1)
            throw new java.util.NoSuchElementException("사용자를 찾을 수 없습니다.");
        return budgets.snapshot(id);
    }
}
