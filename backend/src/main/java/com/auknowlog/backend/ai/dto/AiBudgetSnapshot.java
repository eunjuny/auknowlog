package com.auknowlog.backend.ai.dto;

/** 서버가 보유한 AI 원장을 기준으로 계산한 당일 토큰 예산 현황이다. */
public record AiBudgetSnapshot(
        boolean enforcementEnabled,
        long dailyTokenBudget,
        long todayTokens,
        long remainingTokens,
        int usedPercent
) {
}
