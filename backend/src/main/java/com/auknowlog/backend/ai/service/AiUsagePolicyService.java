package com.auknowlog.backend.ai.service;

import com.auknowlog.backend.ai.dto.AiBudgetSnapshot;
import com.auknowlog.backend.ai.entity.AiGenerationLog;
import com.auknowlog.backend.ai.repository.AiGenerationLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * OpenAI 대시보드의 과금 한도와 별개로, 애플리케이션이 요청 전 차단에 사용하는 안전 예산이다.
 * 실제 토큰은 생성 원장으로 집계하고, 요청 전에는 문자 수/4와 출력 상한을 보수적으로 예약해 판단한다.
 */
@Service
public class AiUsagePolicyService {

    private final AiGenerationLogRepository repository;

    @Value("${auknowlog.ai-policy.enforce-daily-token-budget:true}")
    private boolean enforceDailyTokenBudget = true;

    @Value("${auknowlog.ai-policy.daily-token-budget:50000}")
    private long dailyTokenBudget = 50_000;

    public AiUsagePolicyService(AiGenerationLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AiBudgetSnapshot snapshot() {
        long budget = Math.max(0, dailyTokenBudget);
        long todayTokens = repository.findByCreatedAtGreaterThanEqual(LocalDate.now().atStartOfDay()).stream()
                .mapToLong(log -> tokens(log.getTotalTokens()))
                .sum();
        long remaining = Math.max(0, budget - todayTokens);
        int usedPercent = budget == 0 ? 100 : (int) Math.min(100, Math.round(todayTokens * 100.0 / budget));
        return new AiBudgetSnapshot(enforceDailyTokenBudget, budget, todayTokens, remaining, usedPercent);
    }

    /** 모델 호출 직전, 입력 추정치와 Responses API 출력 상한을 합산해 예산을 검사한다. */
    public void assertWithinBudget(String operation, String input, int maxOutputTokens) {
        if (!enforceDailyTokenBudget) return;
        AiBudgetSnapshot snapshot = snapshot();
        long estimatedInputTokens = estimateTokens(input);
        long projectedTokens = estimatedInputTokens + Math.max(0, maxOutputTokens);
        if (snapshot.todayTokens() + projectedTokens > snapshot.dailyTokenBudget()) {
            throw new IllegalArgumentException(
                    "오늘의 AI 토큰 안전 예산을 초과할 수 있어 생성하지 않았습니다. "
                            + "사용 " + snapshot.todayTokens() + "/" + snapshot.dailyTokenBudget()
                            + " 토큰, 이번 " + operation + " 요청 예상 " + projectedTokens + " 토큰입니다."
            );
        }
    }

    static long estimateTokens(String input) {
        if (input == null || input.isBlank()) return 0;
        return Math.max(1, (input.length() + 3L) / 4L);
    }

    private long tokens(Long value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
