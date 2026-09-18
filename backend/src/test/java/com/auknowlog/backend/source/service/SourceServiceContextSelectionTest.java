package com.auknowlog.backend.source.service;

import com.auknowlog.backend.source.entity.SourceChunk;
import com.auknowlog.backend.source.repository.SourceChunkRepository;
import com.auknowlog.backend.source.repository.SourceDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceServiceContextSelectionTest {

    private SourceDocumentRepository documentRepository;
    private SourceChunkRepository chunkRepository;
    private SourceService service;

    @BeforeEach
    void setUp() {
        documentRepository = mock(SourceDocumentRepository.class);
        chunkRepository = mock(SourceChunkRepository.class);
        service = new SourceService(documentRepository, chunkRepository,
                mock(SourceContentSupport.class), mock(UrlSafetyValidator.class));
        ReflectionTestUtils.setField(service, "quizContextMaxChunks", 2);
        ReflectionTestUtils.setField(service, "quizContextMaxCharacters", 10_000);
    }

    @Test
    void choosesTopicRelevantChunksWithoutCallingEmbeddingApi() {
        when(documentRepository.existsById(7L)).thenReturn(true);
        when(chunkRepository.findBySourceDocumentIdOrderByChunkOrderAsc(7L)).thenReturn(List.of(
                new SourceChunk(null, 1, "이 문서는 서비스 소개와 학습 방법을 설명합니다."),
                new SourceChunk(null, 2, "Kubernetes Pod는 컨테이너 실행의 최소 단위입니다."),
                new SourceChunk(null, 3, "Kubernetes Service는 Pod 집합에 네트워크 주소를 제공합니다.")
        ));

        var contexts = service.getQuizContext(7L, "Kubernetes Pod");

        assertThat(contexts).hasSize(2);
        assertThat(contexts).extracting(context -> context.reference()).containsExactly(
                "source-7-chunk-2", "source-7-chunk-3");
    }
}
