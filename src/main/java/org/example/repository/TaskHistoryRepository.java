package org.example.repository;

import org.example.entity.TaskHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskHistoryRepository extends JpaRepository<TaskHistoryEntity, Long> {
    public List<TaskHistoryEntity> findAllByTaskIdOrderByChangedAtAsc(Long taskId);
}
