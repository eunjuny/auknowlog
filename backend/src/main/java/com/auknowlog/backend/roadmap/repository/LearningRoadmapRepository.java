package com.auknowlog.backend.roadmap.repository;

import com.auknowlog.backend.roadmap.entity.LearningRoadmap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningRoadmapRepository extends JpaRepository<LearningRoadmap, Long> {

    List<LearningRoadmap> findByStatusOrderByCreatedAtDesc(String status);
}
