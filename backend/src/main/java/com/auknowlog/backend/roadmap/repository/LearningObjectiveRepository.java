package com.auknowlog.backend.roadmap.repository;

import com.auknowlog.backend.roadmap.entity.LearningObjective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningObjectiveRepository extends JpaRepository<LearningObjective, Long> {

    List<LearningObjective> findByRoadmapStepIdOrderByObjectiveOrder(Long roadmapStepId);

    List<LearningObjective> findByRoadmapStepRoadmapIdOrderByRoadmapStepStepOrderAscObjectiveOrderAsc(Long roadmapId);

    Optional<LearningObjective> findByRoadmapStepIdAndObjectiveKey(Long roadmapStepId, String objectiveKey);
}
