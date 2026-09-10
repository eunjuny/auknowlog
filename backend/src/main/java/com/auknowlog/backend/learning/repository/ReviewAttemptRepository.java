package com.auknowlog.backend.learning.repository;

import com.auknowlog.backend.learning.entity.ReviewAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewAttemptRepository extends JpaRepository<ReviewAttempt, Long> {
    Optional<ReviewAttempt> findBySubmissionId(UUID submissionId);
}
