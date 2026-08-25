package com.auknowlog.backend.embedding.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 임베딩 호출은 외부 API 비용이 발생할 수 있으므로 기본값은 비활성이다.
 * AUKNOWLOG_EMBEDDINGS_ENABLED=true로 명시할 때만 OpenAI 구현체가 사용된다.
 */
@Service
@ConditionalOnProperty(prefix = "auknowlog.openai.embedding", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledEmbeddingService implements EmbeddingService {

    @Override
    public Optional<EmbeddingResult> embed(String text) {
        return Optional.empty();
    }
}
