package com.auknowlog.backend.daily.service;

import com.auknowlog.backend.daily.entity.DailyLearningFocus;
import com.auknowlog.backend.source.service.UrlSafetyValidator;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 설정된 공개 RSS 하나만 읽는다. URL 검증과 응답 크기 제한은 사용자 URL 수집 경계와 동일하게 적용한다. */
@Service
public class DailyArticleFeedService {
    private static final Set<String> DEVELOPER_CORE_KEYWORDS = Set.of(
            "backend", "백엔드", "frontend", "프론트엔드", "developer", "개발자", "프로그래밍", "코드",
            "java", "spring", "api", "microservice", "마이크로서비스", "msa", "database", "데이터베이스",
            "postgres", "mysql", "kubernetes", "쿠버네티스", "docker", "도커", "devops", "ci/cd", "cicd",
            "aws", "클라우드", "cloud", "배포", "오픈소스", "open source", "github", "소프트웨어", "software",
            "취약점", "랜섬웨어", "보안 패치", "인프라", "observability", "모니터링"
    );
    private static final Set<String> DEVELOPER_ADJACENT_KEYWORDS = Set.of(
            "llm", "생성형 ai", "ai 에이전트", "인공지능", "데이터센터", "gpu", "네트워크", "network",
            "데이터 플랫폼", "데이터 엔지니어", "mcp", "반도체 설계", "서버용", "hbm"
    );
    private static final Set<String> BROAD_IT_KEYWORDS = Set.of(
            "ai", "반도체", "로봇", "통신", "ict", "it", "디지털", "모바일", "디바이스", "플랫폼", "전자", "pcb"
    );
    private final UrlSafetyValidator urlSafetyValidator;
    @Value("${auknowlog.daily-learning.feed-url:https://rss.etnews.com/Section901.xml}") private String feedUrl;
    @Value("${auknowlog.daily-learning.feed.max-bytes:524288}") private int maxBytes;
    @Value("${auknowlog.daily-learning.feed.timeout:8s}") private Duration timeout;

    public DailyArticleFeedService(UrlSafetyValidator urlSafetyValidator) { this.urlSafetyValidator = urlSafetyValidator; }

    public ArticleCandidate latest() {
        URI uri = urlSafetyValidator.validate(feedUrl);
        try {
            var document = Jsoup.connect(uri.toString()).timeout((int) timeout.toMillis()).maxBodySize(maxBytes)
                    .parser(Parser.xmlParser()).get();
            List<ArticleCandidate> candidates = document.select("item").stream().map(item -> new ArticleCandidate(
                            item.selectFirst("title") == null ? "" : item.selectFirst("title").text(),
                            item.selectFirst("link") == null ? "" : item.selectFirst("link").text(),
                            parseDate(item.selectFirst("pubDate") == null ? null : item.selectFirst("pubDate").text()),
                            item.selectFirst("description") == null ? "" : item.selectFirst("description").text(),
                            item.select("category").eachText(), null))
                    .filter(candidate -> !candidate.title().isBlank() && candidate.url().startsWith("https://"))
                    .sorted(Comparator.comparing(ArticleCandidate::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            if (candidates.isEmpty()) throw new IllegalStateException("데일리 학습 RSS에서 사용할 기사를 찾지 못했습니다.");
            return chooseCandidate(candidates);
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) throw (RuntimeException) exception;
            throw new IllegalStateException("데일리 학습 RSS를 읽지 못했습니다.", exception);
        }
    }

    /**
     * 개발 실무 기사를 먼저 고르고, 없을 때만 개발 인접·넓은 IT 기사로 내려간다.
     * RSS 메타데이터만 사용하므로 이 단계에는 AI 호출과 추가 비용이 없다.
     */
    static ArticleCandidate chooseCandidate(List<ArticleCandidate> candidates) {
        return candidates.stream()
                .map(DailyArticleFeedService::classify)
                .max(Comparator.comparingInt(DailyArticleFeedService::priority)
                        .thenComparingInt(DailyArticleFeedService::relevanceScore)
                        .thenComparing(ArticleCandidate::publishedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new IllegalStateException("데일리 학습 RSS에서 사용할 기사를 찾지 못했습니다."));
    }

    private static ArticleCandidate classify(ArticleCandidate candidate) {
        String metadata = String.join(" ", candidate.title(), candidate.description(), String.join(" ", candidate.categories()))
                .toLowerCase(Locale.ROOT);
        DailyLearningFocus focus = containsAny(metadata, DEVELOPER_CORE_KEYWORDS) ? DailyLearningFocus.DEVELOPER_CORE
                : containsAny(metadata, DEVELOPER_ADJACENT_KEYWORDS) ? DailyLearningFocus.DEVELOPER_ADJACENT
                : DailyLearningFocus.IT_EXPANSION;
        return candidate.withFocusTier(focus);
    }

    private static int priority(ArticleCandidate candidate) {
        return switch (candidate.focusTier()) {
            case DEVELOPER_CORE -> 3;
            case DEVELOPER_ADJACENT -> 2;
            case IT_EXPANSION, USER_SELECTED -> 1;
        };
    }

    private static int relevanceScore(ArticleCandidate candidate) {
        String title = candidate.title().toLowerCase(Locale.ROOT);
        String metadata = (candidate.description() + " " + String.join(" ", candidate.categories())).toLowerCase(Locale.ROOT);
        Set<String> keywords = switch (candidate.focusTier()) {
            case DEVELOPER_CORE -> DEVELOPER_CORE_KEYWORDS;
            case DEVELOPER_ADJACENT -> DEVELOPER_ADJACENT_KEYWORDS;
            case IT_EXPANSION, USER_SELECTED -> BROAD_IT_KEYWORDS;
        };
        return countMatches(title, keywords) * 4 + countMatches(metadata, keywords);
    }

    private static boolean containsAny(String text, Set<String> keywords) { return keywords.stream().anyMatch(text::contains); }
    private static int countMatches(String text, Set<String> keywords) { return (int) keywords.stream().filter(text::contains).count(); }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDateTime(); }
        catch (Exception ignored) { return null; }
    }

    public record ArticleCandidate(String title, String url, LocalDateTime publishedAt, String description,
                                   List<String> categories, DailyLearningFocus focusTier) {
        public ArticleCandidate {
            description = description == null ? "" : description;
            categories = categories == null ? List.of() : List.copyOf(categories);
        }
        static ArticleCandidate userSelected(String url) {
            return new ArticleCandidate("데일리 기술 학습 기사", url, null, "", List.of(), DailyLearningFocus.USER_SELECTED);
        }
        ArticleCandidate withFocusTier(DailyLearningFocus focusTier) {
            return new ArticleCandidate(title, url, publishedAt, description, categories, focusTier);
        }
    }
}
