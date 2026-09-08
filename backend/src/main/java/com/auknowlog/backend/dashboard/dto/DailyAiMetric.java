package com.auknowlog.backend.dashboard.dto;

import java.time.LocalDate;

public record DailyAiMetric(LocalDate date, long calls, long totalTokens, long failedCalls) {
}
