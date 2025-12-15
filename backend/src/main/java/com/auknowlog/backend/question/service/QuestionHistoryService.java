package com.auknowlog.backend.question.service;

import com.auknowlog.backend.question.entity.QuestionHistory;
import com.auknowlog.backend.question.repository.QuestionHistoryRepository;
import com.auknowlog.backend.quiz.dto.Question;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class QuestionHistoryService {

    private static final Logger log = LoggerFactory.getLogger(QuestionHistoryService.class);

    private final QuestionHistoryRepository repository;
    private final ObjectMapper objectMapper;

    public QuestionHistoryService(QuestionHistoryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 문제 텍스트를 정규화하고 SHA-256 해시 생성
     */
    public String generateHash(String questionText) {
        // 정규화: 공백 제거, 소문자화, 특수문자 제거
        String normalized = questionText
                .toLowerCase()
                .replaceAll("\\s+", "")
                .replaceAll("[^a-z0-9가-힣]", "");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }

    /**
     * 중복 여부 확인 (리액티브)
     */
    public Mono<Boolean> isDuplicate(String questionText) {
        return Mono.fromCallable(() -> {
            String hash = generateHash(questionText);
            return repository.existsByQuestionHash(hash);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 문제 저장 (중복이면 false 반환)
     */
    @Transactional
    public Mono<Boolean> saveQuestion(String topic, Question question) {
        return Mono.fromCallable(() -> {
            String hash = generateHash(question.questionText());

            // 이미 존재하면 저장하지 않음
            if (repository.existsByQuestionHash(hash)) {
                log.info("중복 문제 감지 (저장 스킵): {}", question.questionText().substring(0, Math.min(50, question.questionText().length())));
                return false;
            }

            try {
                String optionsJson = objectMapper.writeValueAsString(question.options());
                QuestionHistory history = new QuestionHistory(
                        topic,
                        question.questionText(),
                        hash,
                        optionsJson,
                        question.correctAnswer(),
                        question.explanation()
                );
                repository.save(history);
                log.info("문제 저장 완료: {}", question.questionText().substring(0, Math.min(50, question.questionText().length())));
                return true;
            } catch (DataIntegrityViolationException e) {
                // 동시 요청으로 인한 중복 (레이스 컨디션 방어)
                log.warn("동시 요청으로 인한 중복 발생: {}", e.getMessage());
                return false;
            } catch (JsonProcessingException e) {
                log.error("옵션 JSON 변환 실패", e);
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 여러 문제 저장 (퀴즈 전체)
     */
    public Mono<Integer> saveQuestions(String topic, List<Question> questions) {
        return Mono.fromCallable(() -> {
            int savedCount = 0;
            for (Question question : questions) {
                String hash = generateHash(question.questionText());

                if (repository.existsByQuestionHash(hash)) {
                    log.debug("중복 문제 스킵: {}", question.questionText().substring(0, Math.min(30, question.questionText().length())));
                    continue;
                }

                try {
                    String optionsJson = objectMapper.writeValueAsString(question.options());
                    QuestionHistory history = new QuestionHistory(
                            topic,
                            question.questionText(),
                            hash,
                            optionsJson,
                            question.correctAnswer(),
                            question.explanation()
                    );
                    repository.save(history);
                    savedCount++;
                } catch (DataIntegrityViolationException e) {
                    log.debug("동시 중복 스킵");
                } catch (JsonProcessingException e) {
                    log.error("옵션 JSON 변환 실패", e);
                }
            }
            log.info("퀴즈 저장 완료: 총 {}문제 중 {}문제 저장됨 (중복 제외)", questions.size(), savedCount);
            return savedCount;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 주제별 저장된 문제 수 조회
     */
    public Mono<Long> countByTopic(String topic) {
        return Mono.fromCallable(() -> repository.countByTopic(topic))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 전체 저장된 문제 수 조회
     */
    public Mono<Long> countAll() {
        return Mono.fromCallable(repository::count)
                .subscribeOn(Schedulers.boundedElastic());
    }
}

