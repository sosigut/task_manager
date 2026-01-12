package org.example.repository;

import org.example.entity.TaskHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskHistoryRepository extends JpaRepository<TaskHistoryEntity, Integer> {
}
