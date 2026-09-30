package com.auknowlog.backend.learning.repository;

import com.auknowlog.backend.learning.entity.ReviewSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReviewScheduleRepository extends JpaRepository<ReviewSchedule, Long> {

    long countByQuestionQuizOwnerIdAndStatusAndNextReviewAtLessThanEqual(Long ownerId, String status, LocalDateTime nextReviewAt);

    Optional<ReviewSchedule> findByQuestionIdAndStatus(Long questionId, String status);

    List<ReviewSchedule> findTop100ByQuestionQuizOwnerIdAndStatusOrderByNextReviewAtAsc(Long ownerId, String status);

    @Query(value = "SELECT * FROM review_schedule WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<ReviewSchedule> findByIdForUpdate(@Param("id") Long id);

    Optional<ReviewSchedule> findByIdAndQuestionQuizOwnerId(Long id, Long ownerId);
}
