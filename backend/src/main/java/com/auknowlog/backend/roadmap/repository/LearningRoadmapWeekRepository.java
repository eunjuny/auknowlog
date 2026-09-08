package com.auknowlog.backend.roadmap.repository;

import com.auknowlog.backend.roadmap.entity.LearningRoadmapWeek;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningRoadmapWeekRepository extends JpaRepository<LearningRoadmapWeek, Long> {

    List<LearningRoadmapWeek> findByRoadmapIdOrderByWeekNumber(Long roadmapId);
}
