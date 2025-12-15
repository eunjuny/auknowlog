package com.auknowlog.backend.question.repository;

import com.auknowlog.backend.question.document.QuestionDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionDocumentRepository extends ElasticsearchRepository<QuestionDocument, String> {

    // 해시로 조회
    Optional<QuestionDocument> findByQuestionHash(String questionHash);

    // 해시 존재 여부
    boolean existsByQuestionHash(String questionHash);

    // 주제별 조회
    List<QuestionDocument> findByTopic(String topic);
}

