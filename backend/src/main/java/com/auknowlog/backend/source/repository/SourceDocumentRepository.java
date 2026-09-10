package com.auknowlog.backend.source.repository;

import com.auknowlog.backend.source.entity.SourceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SourceDocumentRepository extends JpaRepository<SourceDocument, Long> {
    Optional<SourceDocument> findByContentHash(String contentHash);

    List<SourceDocument> findTop50ByOrderByCreatedAtDesc();
}
