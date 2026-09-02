package com.auknowlog.backend.embedding.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 운영자가 AUKNOWLOG_EMBEDDINGS_ENABLED=false로 명시하면 외부 임베딩 호출 없이
 * SHA-256 정확 중복 검사만 유지한다.
 */
@Service
@ConditionalOnProperty(prefix = "auknowlog.openai.embedding", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledEmbeddingService implements EmbeddingService {

    @Override
    public Optional<EmbeddingResult> embed(String text) {
        return Optional.empty();
    }
}
