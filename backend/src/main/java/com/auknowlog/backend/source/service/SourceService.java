package com.auknowlog.backend.source.service;

import com.auknowlog.backend.source.dto.SourceChunkContext;
import com.auknowlog.backend.source.dto.SourceCreateRequest;
import com.auknowlog.backend.source.dto.SourceCreateResponse;
import com.auknowlog.backend.source.dto.SourceRoadmapContext;
import com.auknowlog.backend.source.dto.SourceSummaryResponse;
import com.auknowlog.backend.source.entity.SourceChunk;
import com.auknowlog.backend.source.entity.SourceDocument;
import com.auknowlog.backend.source.entity.SourceType;
import com.auknowlog.backend.source.repository.SourceChunkRepository;
import com.auknowlog.backend.source.repository.SourceDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SourceService {

    private final SourceDocumentRepository sourceDocumentRepository;
    private final SourceChunkRepository sourceChunkRepository;
    private final SourceContentSupport contentSupport;
    private final UrlSafetyValidator urlSafetyValidator;

    @Value("${auknowlog.ai-policy.source.quiz.max-chunks:4}")
    private int quizContextMaxChunks = 4;

    @Value("${auknowlog.ai-policy.source.quiz.max-characters:4800}")
    private int quizContextMaxCharacters = 4800;

    @Value("${auknowlog.ai-policy.source.roadmap.max-chunks:8}")
    private int roadmapContextMaxChunks = 8;

    @Value("${auknowlog.ai-policy.source.roadmap.max-characters:9600}")
    private int roadmapContextMaxCharacters = 9600;

    public SourceService(SourceDocumentRepository sourceDocumentRepository,
                         SourceChunkRepository sourceChunkRepository,
                         SourceContentSupport contentSupport,
                         UrlSafetyValidator urlSafetyValidator) {
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.sourceChunkRepository = sourceChunkRepository;
        this.contentSupport = contentSupport;
        this.urlSafetyValidator = urlSafetyValidator;
    }

    @Transactional
    public SourceCreateResponse create(SourceCreateRequest request) {
        String normalizedContent = contentSupport.normalize(request.content());
        String contentHash = contentSupport.sha256(normalizedContent);
        SourceDocument existing = sourceDocumentRepository.findByContentHash(contentHash).orElse(null);
        if (existing != null) {
            return new SourceCreateResponse(
                    existing.getId(),
                    existing.getTitle(),
                    sourceChunkRepository.countBySourceDocumentId(existing.getId()),
                    existing.getSourceType(),
                    true
            );
        }

        SourceType sourceType = request.sourceType() == null ? SourceType.TEXT : request.sourceType();
        String sourceUri = sourceType == SourceType.URL ? validatedUri(request.sourceUri()) : null;
        String originalName = sourceType == SourceType.FILE ? normalizedOriginalName(request.originalName()) : null;
        String mimeType = normalizedMimeType(request.mimeType(), sourceType);
        SourceDocument document = sourceDocumentRepository.save(new SourceDocument(
                request.title().trim(),
                normalizedContent,
                sourceType,
                sourceUri,
                originalName,
                mimeType,
                contentHash
        ));
        List<String> chunks = contentSupport.splitIntoChunks(normalizedContent);
        for (int index = 0; index < chunks.size(); index++) {
            sourceChunkRepository.save(new SourceChunk(document, index + 1, chunks.get(index)));
        }
        return new SourceCreateResponse(document.getId(), document.getTitle(), chunks.size(), sourceType, false);
    }

    @Transactional(readOnly = true)
    public List<SourceChunkContext> getQuizContext(Long sourceId) {
        return getQuizContext(sourceId, "");
    }

    @Transactional(readOnly = true)
    public List<SourceChunkContext> getQuizContext(Long sourceId, String topic) {
        if (!sourceDocumentRepository.existsById(sourceId)) {
            throw new java.util.NoSuchElementException("학습 자료를 찾을 수 없습니다.");
        }
        return selectRelevantChunkContexts(sourceId, topic, quizContextMaxChunks, quizContextMaxCharacters);
    }

    @Transactional(readOnly = true)
    public SourceRoadmapContext getRoadmapContext(Long sourceId) {
        return getRoadmapContext(sourceId, "");
    }

    @Transactional(readOnly = true)
    public SourceRoadmapContext getRoadmapContext(Long sourceId, String topic) {
        SourceDocument document = sourceDocumentRepository.findById(sourceId)
                .orElseThrow(() -> new java.util.NoSuchElementException("학습 자료를 찾을 수 없습니다."));
        List<SourceChunkContext> chunks = selectRelevantChunkContexts(
                sourceId, topic, roadmapContextMaxChunks, roadmapContextMaxCharacters);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("학습 자료에 로드맵 생성용 본문이 없습니다.");
        }
        return new SourceRoadmapContext(
                document.getId(),
                document.getTitle(),
                document.getSourceType(),
                document.getSourceUri(),
                chunks
        );
    }

    /** 임베딩 API를 추가 호출하지 않고, 주제 키워드와 문자 예산으로 자료 문맥을 고른다. */
    private List<SourceChunkContext> selectRelevantChunkContexts(Long sourceId,
                                                                   String topic,
                                                                   int maxChunks,
                                                                   int maxCharacters) {
        List<SourceChunk> allChunks = sourceChunkRepository.findBySourceDocumentIdOrderByChunkOrderAsc(sourceId);
        Set<String> terms = topicTerms(topic);
        List<SourceChunk> ranked = allChunks.stream()
                .sorted(Comparator.comparingInt((SourceChunk chunk) -> relevanceScore(chunk, terms)).reversed()
                        .thenComparingInt(SourceChunk::getChunkOrder))
                .limit(Math.max(1, maxChunks))
                .sorted(Comparator.comparingInt(SourceChunk::getChunkOrder))
                .toList();
        int remaining = Math.max(1, maxCharacters);
        List<SourceChunkContext> selected = new ArrayList<>();
        for (SourceChunk chunk : ranked) {
            if (remaining <= 0) {
                break;
            }
            String content = chunk.getContent();
            String selectedContent = content.length() <= remaining
                    ? content
                    : content.substring(0, remaining);
            selected.add(new SourceChunkContext(
                    "source-" + sourceId + "-chunk-" + chunk.getChunkOrder(),
                    selectedContent
            ));
            remaining -= selectedContent.length();
        }
        return selected;
    }

    private Set<String> topicTerms(String topic) {
        if (topic == null || topic.isBlank()) return Set.of();
        return Arrays.stream(topic.toLowerCase(Locale.ROOT).split("[^a-z0-9가-힣]+"))
                .filter(term -> term.length() >= 2)
                .collect(Collectors.toUnmodifiableSet());
    }

    private int relevanceScore(SourceChunk chunk, Set<String> terms) {
        if (terms.isEmpty()) return 0;
        String content = chunk.getContent().toLowerCase(Locale.ROOT);
        return (int) terms.stream().filter(content::contains).count();
    }

    @Transactional(readOnly = true)
    public List<SourceSummaryResponse> getSources() {
        List<SourceDocument> documents = sourceDocumentRepository.findTop50ByOrderByCreatedAtDesc();
        if (documents.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> chunkCounts = sourceChunkRepository
                .countBySourceDocumentIds(documents.stream().map(SourceDocument::getId).toList())
                .stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).intValue()
                ));
        return documents.stream()
                .map(document -> new SourceSummaryResponse(
                        document.getId(),
                        document.getTitle(),
                        document.getSourceType(),
                        document.getSourceUri(),
                        document.getOriginalName(),
                        document.getMimeType(),
                        document.getContent().length(),
                        chunkCounts.getOrDefault(document.getId(), 0),
                        document.getCreatedAt()
                ))
                .toList();
    }

    private String validatedUri(String sourceUri) {
        if (sourceUri == null || sourceUri.isBlank()) {
            throw new IllegalArgumentException("URL 자료에는 출처 URL이 필요합니다.");
        }
        return urlSafetyValidator.validate(sourceUri).toString();
    }

    private String normalizedOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("파일 자료에는 원본 파일명이 필요합니다.");
        }
        String normalized = originalName.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (name.isBlank() || name.length() > 255) {
            throw new IllegalArgumentException("원본 파일명은 255자 이하여야 합니다.");
        }
        return name;
    }

    private String normalizedMimeType(String mimeType, SourceType sourceType) {
        if (mimeType == null || mimeType.isBlank()) {
            return sourceType == SourceType.TEXT ? "text/plain" : "application/octet-stream";
        }
        String normalized = mimeType.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")) {
            throw new IllegalArgumentException("MIME 타입 형식이 올바르지 않습니다.");
        }
        return normalized;
    }
}
