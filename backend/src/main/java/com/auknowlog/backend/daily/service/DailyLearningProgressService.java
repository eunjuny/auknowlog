package com.auknowlog.backend.daily.service;

import com.auknowlog.backend.daily.entity.DailyLearningTrack;
import com.auknowlog.backend.daily.repository.DailyLearningRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyLearningProgressService {
    private final DailyLearningRepository repository;
    public DailyLearningProgressService(DailyLearningRepository repository) { this.repository = repository; }

    @Transactional
    public void recordSubmittedQuiz(Long dailyLearningId, DailyLearningTrack track) {
        if (dailyLearningId == null || track != DailyLearningTrack.REVIEW) return;
        repository.findById(dailyLearningId).ifPresent(daily -> daily.complete());
    }
}
