package com.auknowlog.backend.learning.repository;

import com.auknowlog.backend.learning.entity.ReviewSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ReviewScheduleRepository extends JpaRepository<ReviewSchedule, Long> {

    long countByStatusAndNextReviewAtLessThanEqual(String status, LocalDateTime nextReviewAt);
}
