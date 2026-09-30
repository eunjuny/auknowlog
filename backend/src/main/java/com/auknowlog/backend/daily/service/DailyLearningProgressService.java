package com.auknowlog.backend.daily.service;

import com.auknowlog.backend.auth.service.CurrentUserService;
import com.auknowlog.backend.daily.entity.DailyLearningProgress;
import com.auknowlog.backend.daily.entity.DailyLearningTrack;
import com.auknowlog.backend.daily.repository.DailyLearningRepository;
import com.auknowlog.backend.daily.repository.DailyLearningProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyLearningProgressService {
    private final DailyLearningRepository repository;
    private final DailyLearningProgressRepository progressRepository;
    private final CurrentUserService currentUserService;
    public DailyLearningProgressService(DailyLearningRepository repository,
                                        DailyLearningProgressRepository progressRepository,
                                        CurrentUserService currentUserService) {
        this.repository = repository;
        this.progressRepository = progressRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public void recordSubmittedQuiz(Long dailyLearningId, DailyLearningTrack track) {
        if (dailyLearningId == null || track != DailyLearningTrack.REVIEW) return;
        repository.findById(dailyLearningId).ifPresent(daily -> progressRepository
                .findByDailyLearningIdAndOwnerId(dailyLearningId, currentUserService.currentUserId())
                .orElseGet(() -> progressRepository.save(new DailyLearningProgress(daily, currentUserService.currentUser())))
                .complete());
    }

    @Transactional(readOnly = true)
    public java.util.Optional<DailyLearningProgress> findProgress(Long dailyLearningId) {
        return progressRepository.findByDailyLearningIdAndOwnerId(dailyLearningId, currentUserService.currentUserId());
    }
}
