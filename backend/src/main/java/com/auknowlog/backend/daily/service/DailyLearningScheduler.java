package com.auknowlog.backend.daily.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyLearningScheduler {
    private static final Logger log = LoggerFactory.getLogger(DailyLearningScheduler.class);
    private final DailyLearningService service;
    @Value("${auknowlog.daily-learning.enabled:true}") private boolean enabled;
    public DailyLearningScheduler(DailyLearningService service) { this.service = service; }

    @Scheduled(cron = "${auknowlog.daily-learning.cron:0 30 7 * * *}", zone = "${auknowlog.daily-learning.zone:Asia/Seoul}")
    public void generateMorningLearning() {
        if (!enabled) return;
        try { service.generateToday(null); }
        catch (Exception exception) { log.warn("Daily learning generation did not complete; it can be retried from the UI", exception); }
    }
}
