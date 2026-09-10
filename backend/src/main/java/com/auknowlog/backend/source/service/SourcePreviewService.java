package com.auknowlog.backend.source.service;

import com.auknowlog.backend.common.observability.SourceIngestionMetrics;
import com.auknowlog.backend.source.dto.SourcePreviewResponse;
import com.auknowlog.backend.source.entity.SourceDocument;
import com.auknowlog.backend.source.entity.SourceType;
import com.auknowlog.backend.source.repository.SourceDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class SourcePreviewService {

    private final SourceContentExtractor contentExtractor;
    private final SourceContentSupport contentSupport;
    private final UrlSourceFetcher urlSourceFetcher;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final SourceIngestionMetrics metrics;
    private final int maxFileBytes;

    public SourcePreviewService(
            SourceContentExtractor contentExtractor,
            SourceContentSupport contentSupport,
            UrlSourceFetcher urlSourceFetcher,
            SourceDocumentRepository sourceDocumentRepository,
            SourceIngestionMetrics metrics,
            @Value("${auknowlog.source.file.max-bytes:10485760}") int maxFileBytes) {
        this.contentExtractor = contentExtractor;
        this.contentSupport = contentSupport;
        this.urlSourceFetcher = urlSourceFetcher;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.metrics = metrics;
        this.maxFileBytes = maxFileBytes;
    }

    public SourcePreviewResponse previewFile(MultipartFile file) {
        Instant startedAt = Instant.now();
        long inputBytes = file == null ? 0 : file.getSize();
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("업로드할 파일을 선택해주세요.");
            }
            if (file.getSize() > maxFileBytes) {
                throw new IllegalArgumentException("파일은 최대 " + maxFileBytes / 1024 / 1024 + "MB까지 업로드할 수 있습니다.");
            }
            ExtractedSource extracted = contentExtractor.extractFile(file.getOriginalFilename(), file.getBytes());
            SourcePreviewResponse response = response(
                    SourceType.FILE,
                    extracted,
                    null,
                    safeOriginalName(file.getOriginalFilename())
            );
            metrics.record("file", "success", inputBytes, response.contentLength(), Duration.between(startedAt, Instant.now()));
            return response;
        } catch (IllegalArgumentException exception) {
            metrics.record("file", "rejected", inputBytes, 0, Duration.between(startedAt, Instant.now()));
            throw exception;
        } catch (IOException exception) {
            metrics.record("file", "failure", inputBytes, 0, Duration.between(startedAt, Instant.now()));
            throw new IllegalArgumentException("업로드한 파일을 읽지 못했습니다.");
        } catch (RuntimeException exception) {
            metrics.record("file", "failure", inputBytes, 0, Duration.between(startedAt, Instant.now()));
            throw exception;
        }
    }

    public SourcePreviewResponse previewUrl(String url) {
        Instant startedAt = Instant.now();
        try {
            FetchedSource fetched = urlSourceFetcher.fetch(url);
            ExtractedSource extracted = contentExtractor.extractUrl(fetched);
            SourcePreviewResponse response = response(
                    SourceType.URL,
                    extracted,
                    fetched.finalUri().toString(),
                    null
            );
            metrics.record("url", "success", fetched.body().length, response.contentLength(), Duration.between(startedAt, Instant.now()));
            return response;
        } catch (IllegalArgumentException exception) {
            metrics.record("url", "rejected", 0, 0, Duration.between(startedAt, Instant.now()));
            throw exception;
        } catch (RuntimeException exception) {
            metrics.record("url", "failure", 0, 0, Duration.between(startedAt, Instant.now()));
            throw exception;
        }
    }

    public SourcePreviewResponse previewText(String title, String content) {
        String normalized = contentSupport.normalize(content);
        return response(
                SourceType.TEXT,
                new ExtractedSource(title.trim(), normalized, "text/plain"),
                null,
                null
        );
    }

    private SourcePreviewResponse response(
            SourceType sourceType,
            ExtractedSource extracted,
            String sourceUri,
            String originalName) {
        String contentHash = contentSupport.sha256(extracted.content());
        Optional<SourceDocument> existing = sourceDocumentRepository.findByContentHash(contentHash);
        return new SourcePreviewResponse(
                sourceType,
                extracted.title(),
                extracted.content(),
                sourceUri,
                originalName,
                extracted.mimeType(),
                extracted.content().length(),
                contentSupport.splitIntoChunks(extracted.content()).size(),
                contentHash,
                existing.isPresent(),
                existing.map(SourceDocument::getId).orElse(null)
        );
    }

    private String safeOriginalName(String originalName) {
        if (originalName == null) {
            return null;
        }
        String normalized = originalName.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        return name.substring(0, Math.min(name.length(), 255));
    }
}
