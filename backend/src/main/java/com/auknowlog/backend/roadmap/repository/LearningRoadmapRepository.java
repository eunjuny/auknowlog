package com.auknowlog.backend.roadmap.repository;

import com.auknowlog.backend.roadmap.entity.LearningRoadmap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningRoadmapRepository extends JpaRepository<LearningRoadmap, Long> {

    List<LearningRoadmap> findByOwnerIdAndStatusOrderByCreatedAtDesc(Long ownerId, String status);

    List<LearningRoadmap> findAllByStatusOrderByCreatedAtDesc(String status);

    java.util.Optional<LearningRoadmap> findByIdAndOwnerId(Long id, Long ownerId);
}
