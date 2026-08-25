package com.auknowlog.backend.source.service;

import com.auknowlog.backend.source.dto.SourceChunkContext;
import com.auknowlog.backend.source.dto.SourceCreateRequest;
import com.auknowlog.backend.source.dto.SourceCreateResponse;
import com.auknowlog.backend.source.entity.SourceChunk;
import com.auknowlog.backend.source.entity.SourceDocument;
import com.auknowlog.backend.source.repository.SourceChunkRepository;
import com.auknowlog.backend.source.repository.SourceDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SourceService {

    private static final int CHUNK_SIZE = 1_200;

    private final SourceDocumentRepository sourceDocumentRepository;
    private final SourceChunkRepository sourceChunkRepository;

    public SourceService(SourceDocumentRepository sourceDocumentRepository,
                         SourceChunkRepository sourceChunkRepository) {
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.sourceChunkRepository = sourceChunkRepository;
    }

    @Transactional
    public SourceCreateResponse create(SourceCreateRequest request) {
        SourceDocument document = sourceDocumentRepository.save(new SourceDocument(
                request.title().trim(), request.content().trim()));
        List<String> chunks = splitIntoChunks(request.content().trim());
        for (int index = 0; index < chunks.size(); index++) {
            sourceChunkRepository.save(new SourceChunk(document, index + 1, chunks.get(index)));
        }
        return new SourceCreateResponse(document.getId(), document.getTitle(), chunks.size());
    }

    @Transactional(readOnly = true)
    public List<SourceChunkContext> getQuizContext(Long sourceId) {
        if (!sourceDocumentRepository.existsById(sourceId)) {
            throw new java.util.NoSuchElementException("학습 자료를 찾을 수 없습니다.");
        }
        return sourceChunkRepository.findTop4BySourceDocumentIdOrderByChunkOrderAsc(sourceId).stream()
                .map(chunk -> new SourceChunkContext("source-" + sourceId + "-chunk-" + chunk.getChunkOrder(), chunk.getContent()))
                .toList();
    }

    private List<String> splitIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : content.split("\\R+")) {
            String normalized = paragraph.trim();
            if (normalized.isBlank()) {
                continue;
            }
            if (current.length() > 0 && current.length() + normalized.length() + 1 > CHUNK_SIZE) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (normalized.length() > CHUNK_SIZE) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                for (int start = 0; start < normalized.length(); start += CHUNK_SIZE) {
                    chunks.add(normalized.substring(start, Math.min(normalized.length(), start + CHUNK_SIZE)));
                }
                continue;
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(normalized);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks.isEmpty() ? List.of(content) : chunks;
    }
}
