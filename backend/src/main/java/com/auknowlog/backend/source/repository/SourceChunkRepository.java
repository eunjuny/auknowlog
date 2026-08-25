package com.auknowlog.backend.source.repository;

import com.auknowlog.backend.source.entity.SourceChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceChunkRepository extends JpaRepository<SourceChunk, Long> {

    List<SourceChunk> findTop4BySourceDocumentIdOrderByChunkOrderAsc(Long sourceDocumentId);
}
