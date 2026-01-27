package org.example.repository;

import org.example.entity.CommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface CommentRepository extends JpaRepository<CommentEntity,Long> {
    public List<CommentEntity> findAllByTaskIdOrderByCreatedAtAsc(Long taskId);
}
