package com.auknowlog.backend.daily.service;

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

/** 설정된 공개 RSS 하나만 읽는다. URL 검증과 응답 크기 제한은 사용자 URL 수집 경계와 동일하게 적용한다. */
@Service
public class DailyArticleFeedService {
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
                            parseDate(item.selectFirst("pubDate") == null ? null : item.selectFirst("pubDate").text())))
                    .filter(candidate -> !candidate.title().isBlank() && candidate.url().startsWith("https://"))
                    .sorted(Comparator.comparing(ArticleCandidate::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            if (candidates.isEmpty()) throw new IllegalStateException("데일리 학습 RSS에서 사용할 기사를 찾지 못했습니다.");
            return candidates.stream().filter(this::isTechnical).findFirst().orElse(candidates.getFirst());
        } catch (Exception exception) {
            if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) throw (RuntimeException) exception;
            throw new IllegalStateException("데일리 학습 RSS를 읽지 못했습니다.", exception);
        }
    }

    /** 일반 RSS에서도 개발 학습 맥락이 있는 기사를 우선한다. 키워드는 환경 변수로 조정할 수 있다. */
    private boolean isTechnical(ArticleCandidate candidate) {
        String title = candidate.title().toLowerCase(Locale.ROOT);
        return List.of("ai", "인공지능", "클라우드", "서버", "보안", "소프트웨어", "데이터", "반도체", "네트워크", "로봇", "pcb", "컴퓨팅")
                .stream().anyMatch(title::contains);
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDateTime(); }
        catch (Exception ignored) { return null; }
    }

    public record ArticleCandidate(String title, String url, LocalDateTime publishedAt) { }
}
