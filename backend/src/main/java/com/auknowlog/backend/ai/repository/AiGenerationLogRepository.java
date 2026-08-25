package com.auknowlog.backend.ai.repository;

import com.auknowlog.backend.ai.entity.AiGenerationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGenerationLogRepository extends JpaRepository<AiGenerationLog, Long> {
}
