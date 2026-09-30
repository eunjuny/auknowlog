package com.auknowlog.backend.daily.repository;

import com.auknowlog.backend.daily.entity.DailyLearningProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DailyLearningProgressRepository extends JpaRepository<DailyLearningProgress, Long> {
    Optional<DailyLearningProgress> findByDailyLearningIdAndOwnerId(Long dailyLearningId, Long ownerId);
}
