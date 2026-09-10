package com.auknowlog.backend.source.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceContentExtractorTest {

    private SourceContentExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new SourceContentExtractor(new SourceContentSupport(50_000), 50_000);
    }

    @Test
    void extractsArticleAndRemovesExecutableOrNavigationElements() {
        String html = """
                <html><head><title>Kubernetes Service 학습</title></head><body>
                <nav>로그인 메뉴와 광고 문구는 학습 본문이 아닙니다.</nav>
                <script>window.secret = 'do-not-keep';</script>
                <article>
                  <h1>Service 기본 개념</h1>
                  <p>Service는 여러 Pod에 대한 안정적인 네트워크 엔드포인트를 제공합니다.</p>
                  <p>ClusterIP는 클러스터 내부 통신에 사용하는 기본 Service 유형입니다.</p>
                </article>
                <section><p>매 요청마다 바뀌는 추천 기사와 인기 검색어는 저장하면 안 됩니다.</p></section>
                </body></html>
                """;
        FetchedSource fetched = new FetchedSource(
                URI.create("https://docs.example.com/kubernetes/service"),
                html.getBytes(StandardCharsets.UTF_8),
                "text/html; charset=utf-8",
                "service"
        );

        ExtractedSource result = extractor.extractUrl(fetched);

        assertThat(result.title()).isEqualTo("Service 기본 개념");
        assertThat(result.content()).contains("안정적인 네트워크 엔드포인트", "ClusterIP");
        assertThat(result.content()).doesNotContain("로그인 메뉴", "window.secret", "추천 기사");
        assertThat(result.mimeType()).isEqualTo("text/html");
    }

    @Test
    void acceptsMarkdownByExtensionButRejectsDisguisedBinary() {
        ExtractedSource markdown = extractor.extractFile(
                "kubernetes.md",
                "# Pod\nPod는 Kubernetes의 최소 배포 단위입니다.".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(markdown.title()).isEqualTo("kubernetes");
        assertThat(markdown.mimeType()).isEqualTo("text/markdown");
        assertThatThrownBy(() -> extractor.extractFile("malware.txt", new byte[]{0, 1, 2, 3, 4, 5}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("위장");
    }
}
