package com.auknowlog.backend.dashboard.dto;

public record ModelAiMetric(String model, long calls, long totalTokens, int successRatePercent, long averageLatencyMs) {
}
