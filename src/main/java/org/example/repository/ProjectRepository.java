package org.example.repository;

import org.example.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {
    public List<ProjectEntity> findAllByOwnerId(Long ownerId);
}
