package com.auknowlog.backend.ai.repository;

import com.auknowlog.backend.ai.entity.AiGenerationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AiGenerationLogRepository extends JpaRepository<AiGenerationLog, Long> {

    List<AiGenerationLog> findByCreatedAtGreaterThanEqual(LocalDateTime createdAt);
}
