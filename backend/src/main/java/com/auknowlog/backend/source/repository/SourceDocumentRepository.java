package com.auknowlog.backend.source.repository;

import com.auknowlog.backend.source.entity.SourceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SourceDocumentRepository extends JpaRepository<SourceDocument, Long> {
    Optional<SourceDocument> findByOwnerIdAndContentHash(Long ownerId, String contentHash);

    List<SourceDocument> findTop50ByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<SourceDocument> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByIdAndOwnerId(Long id, Long ownerId);

    @org.springframework.data.jpa.repository.Query("select document from SourceDocument document where document.id = :id and (document.owner.id = :ownerId or document.shared = true)")
    Optional<SourceDocument> findAccessibleById(Long id, Long ownerId);

    @org.springframework.data.jpa.repository.Query("select count(document) > 0 from SourceDocument document where document.id = :id and (document.owner.id = :ownerId or document.shared = true)")
    boolean existsAccessibleById(Long id, Long ownerId);
}
