package com.auknowlog.backend.source.repository;

import com.auknowlog.backend.source.entity.SourceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceDocumentRepository extends JpaRepository<SourceDocument, Long> {
}
