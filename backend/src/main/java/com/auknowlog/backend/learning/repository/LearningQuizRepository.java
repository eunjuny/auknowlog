package com.auknowlog.backend.learning.repository;

import com.auknowlog.backend.learning.entity.LearningQuiz;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningQuizRepository extends JpaRepository<LearningQuiz, Long> {
}
