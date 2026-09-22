package com.auknowlog.backend.learning.repository;

import com.auknowlog.backend.learning.entity.LearningQuiz;
import org.springframework.data.jpa.repository.JpaRepository;

import com.auknowlog.backend.daily.entity.DailyLearningTrack;

public interface LearningQuizRepository extends JpaRepository<LearningQuiz, Long> {

    java.util.Optional<LearningQuiz> findFirstByDailyLearningIdAndDailyLearningTrackOrderByIdDesc(Long dailyLearningId,
                                                                                                     DailyLearningTrack track);

    long countByDailyLearningIdAndDailyLearningTrack(Long dailyLearningId, DailyLearningTrack track);
}
