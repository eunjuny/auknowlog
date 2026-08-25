package com.auknowlog.backend.learning.repository;

import com.auknowlog.backend.learning.entity.LearningAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningAttemptAnswerRepository extends JpaRepository<LearningAttemptAnswer, Long> {
}
