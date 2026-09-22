package com.auknowlog.backend.daily.repository;

import com.auknowlog.backend.daily.entity.DailyLearning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyLearningRepository extends JpaRepository<DailyLearning, Long> {
    Optional<DailyLearning> findByLearningDate(LocalDate learningDate);
    Optional<DailyLearning> findByArticleUrl(String articleUrl);

    @Query("select daily from DailyLearning daily join fetch daily.sourceDocument where daily.id = :id")
    Optional<DailyLearning> findDetailedById(Long id);
}
