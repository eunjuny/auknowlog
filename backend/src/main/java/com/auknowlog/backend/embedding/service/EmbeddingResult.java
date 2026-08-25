package com.auknowlog.backend.embedding.service;

public record EmbeddingResult(String model, float[] values, long inputTokens) {
}
