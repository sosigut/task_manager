package org.example.repository;

import org.example.entity.TaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<TaskEntity,Long> {
    public List<TaskEntity> findAllByProjectId(Long projectId);
    public List<TaskEntity> findAllByProjectIdAndAssigneeId(Long projectId, Long assigneeId);
}
