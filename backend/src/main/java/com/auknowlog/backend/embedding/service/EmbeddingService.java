package com.auknowlog.backend.embedding.service;

import java.util.Optional;

public interface EmbeddingService {

    Optional<EmbeddingResult> embed(String text);
}
