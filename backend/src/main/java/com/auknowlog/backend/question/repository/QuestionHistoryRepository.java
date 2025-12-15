package com.auknowlog.backend.question.repository;

import com.auknowlog.backend.question.entity.QuestionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionHistoryRepository extends JpaRepository<QuestionHistory, Long> {

    // 해시로 중복 체크
    boolean existsByQuestionHash(String questionHash);

    // 해시로 조회
    Optional<QuestionHistory> findByQuestionHash(String questionHash);

    // 주제별 조회
    List<QuestionHistory> findByTopic(String topic);

    // 주제별 개수
    long countByTopic(String topic);
}

