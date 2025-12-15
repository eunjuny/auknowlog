package com.auknowlog.backend.question.service;

import com.auknowlog.backend.question.document.QuestionDocument;
import com.auknowlog.backend.question.repository.QuestionDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QuestionSearchService {

    private static final Logger log = LoggerFactory.getLogger(QuestionSearchService.class);
    private static final double DEFAULT_THRESHOLD = 0.7;

    private final QuestionDocumentRepository repository;
    private final ElasticsearchOperations esOps;
    private final ObjectMapper mapper;

    public QuestionSearchService(QuestionDocumentRepository repo, ElasticsearchOperations esOps, ObjectMapper mapper) {
        this.repository = repo;
        this.esOps = esOps;
        this.mapper = mapper;
    }

    public Mono<List<SimilarQuestion>> findSimilar(String questionText, double threshold) {
        return Mono.fromCallable(() -> {
            Query query = new CriteriaQuery(Criteria.where("questionText").matches(questionText));
            SearchHits<QuestionDocument> hits = esOps.search(query, QuestionDocument.class);
            return hits.getSearchHits().stream()
                    .filter(h -> normalize(h.getScore()) >= threshold)
                    .map(h -> new SimilarQuestion(h.getContent(), normalize(h.getScore())))
                    .collect(Collectors.toList());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<DuplicateCheckResult> checkDuplicate(String questionText) {
        String hash = hash(questionText);
        return Mono.fromCallable(() -> repository.existsByQuestionHash(hash))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(exact -> {
                    if (exact) return Mono.just(new DuplicateCheckResult(true, 1.0, "동일 질문 존재", null));
                    return findSimilar(questionText, DEFAULT_THRESHOLD).map(list -> {
                        if (list.isEmpty()) return new DuplicateCheckResult(false, 0, null, null);
                        SimilarQuestion top = list.get(0);
                        return top.score >= DEFAULT_THRESHOLD
                                ? new DuplicateCheckResult(true, top.score, "유사 질문 발견", top.doc)
                                : new DuplicateCheckResult(false, top.score, null, null);
                    });
                });
    }

    public Mono<QuestionDocument> index(String topic, String qText, List<String> opts, String ans, String expl) {
        return Mono.fromCallable(() -> {
            QuestionDocument doc = new QuestionDocument(UUID.randomUUID().toString(), topic, qText,
                    hash(qText), mapper.writeValueAsString(opts), ans, expl);
            return repository.save(doc);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @SuppressWarnings("unchecked")
    public Mono<List<QuestionDocument>> indexQuestions(String topic, List<Map<String, Object>> questions) {
        return Flux.fromIterable(questions)
                .flatMap(q -> checkDuplicate((String) q.get("questionText"))
                        .flatMap(r -> r.dup ? Mono.empty()
                                : index(topic, (String) q.get("questionText"), (List<String>) q.get("options"),
                                        (String) q.get("correctAnswer"), (String) q.getOrDefault("explanation", ""))))
                .collectList();
    }

    private String hash(String text) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private double normalize(float score) { return 1.0 / (1.0 + Math.exp(-score + 5)); }

    public record SimilarQuestion(QuestionDocument doc, double score) {}
    public record DuplicateCheckResult(boolean dup, double score, String msg, QuestionDocument similar) {}
}

