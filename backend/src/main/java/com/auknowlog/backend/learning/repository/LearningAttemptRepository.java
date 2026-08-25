package com.auknowlog.backend.learning.repository;

import com.auknowlog.backend.learning.entity.LearningAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningAttemptRepository extends JpaRepository<LearningAttempt, Long> {
}
