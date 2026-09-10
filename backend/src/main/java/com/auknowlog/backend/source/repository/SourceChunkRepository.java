package com.auknowlog.backend.source.repository;

import com.auknowlog.backend.source.entity.SourceChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SourceChunkRepository extends JpaRepository<SourceChunk, Long> {

    List<SourceChunk> findTop4BySourceDocumentIdOrderByChunkOrderAsc(Long sourceDocumentId);

    List<SourceChunk> findTop8BySourceDocumentIdOrderByChunkOrderAsc(Long sourceDocumentId);

    int countBySourceDocumentId(Long sourceDocumentId);

    @Query("""
            select chunk.sourceDocument.id, count(chunk.id)
            from SourceChunk chunk
            where chunk.sourceDocument.id in :sourceIds
            group by chunk.sourceDocument.id
            """)
    List<Object[]> countBySourceDocumentIds(@Param("sourceIds") Collection<Long> sourceIds);
}
