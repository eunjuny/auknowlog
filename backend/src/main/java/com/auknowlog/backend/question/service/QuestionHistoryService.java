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
     * 중복 여부 확인
     */
    public boolean isDuplicate(String questionText) {
        String hash = generateHash(questionText);
        return repository.existsByQuestionHash(hash);
    }

    /**
     * 문제 저장 (중복이면 false 반환)
     */
    @Transactional
    public boolean saveQuestion(String topic, Question question) {
        String hash = generateHash(question.questionText());

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
            log.warn("동시 요청으로 인한 중복 발생: {}", e.getMessage());
            return false;
        } catch (JsonProcessingException e) {
            log.error("옵션 JSON 변환 실패", e);
            return false;
        }
    }

    /**
     * 여러 문제 저장 (퀴즈 전체)
     */
    @Transactional
    public int saveQuestions(String topic, List<Question> questions) {
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
    }

    /**
     * 주제별 저장된 문제 수 조회
     */
    public long countByTopic(String topic) {
        return repository.countByTopic(topic);
    }

    /**
     * 전체 저장된 문제 수 조회
     */
    public long countAll() {
        return repository.count();
    }

    /**
     * 주제별 최근 문제 목록 조회 (토큰 최소화: 앞 50자만, 최대 30개)
     * 프롬프트에 포함하여 중복 방지용
     */
    public List<String> getRecentQuestionPreviews(String topic, int limit) {
        List<QuestionHistory> recent = repository.findByTopic(topic);
        return recent.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())) // 최신순
                .limit(limit)
                .map(q -> {
                    String text = q.getQuestionText();
                    // 토큰 최소화: 앞 50자만 추출
                    return text.length() > 50 ? text.substring(0, 50) + "..." : text;
                })
                .toList();
    }
}
