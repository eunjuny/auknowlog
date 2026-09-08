package com.auknowlog.backend.roadmap.repository;

import com.auknowlog.backend.roadmap.entity.LearningRoadmapStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LearningRoadmapStepRepository extends JpaRepository<LearningRoadmapStep, Long> {

    @Query("""
            select distinct step from LearningRoadmapStep step
            left join fetch step.prerequisites
            where step.roadmap.id = :roadmapId
            order by step.stepOrder
            """)
    List<LearningRoadmapStep> findWithPrerequisitesByRoadmapIdOrderByStepOrder(@Param("roadmapId") Long roadmapId);
}
