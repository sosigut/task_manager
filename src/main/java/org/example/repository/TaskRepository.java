package org.example.repository;

import org.example.entity.TaskEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

    @Query("""
        select t from TaskEntity t
        where t.project.id = :projectId
        order by t.createdAt desc, t.id desc
    """)
    Slice<TaskEntity> findFirstPageByProjectId(
            @Param("projectId") Long projectId,
            Pageable pageable
    );

    @Query("""
        select t from TaskEntity t
        where t.project.id = :projectId
          and t.assignee.id = :assigneeId
        order by t.createdAt desc, t.id desc
    """)
    Slice<TaskEntity> findFirstPageByProjectIdAndAssigneeId(
            @Param("projectId") Long projectId,
            @Param("assigneeId") Long assigneeId,
            Pageable pageable
    );



    @Query("""
        select t from TaskEntity t
        where t.project.id = :projectId
          and (
              t.createdAt < :cursorCreatedAt
              or (t.createdAt = :cursorCreatedAt and t.id < :cursorId)
          )
        order by t.createdAt desc, t.id desc
    """)
    Slice<TaskEntity> findNextByProjectIdAfterCursor(
            @Param("projectId") Long projectId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
        select t from TaskEntity t
        where t.project.id = :projectId
          and t.assignee.id = :assigneeId
          and (
              t.createdAt < :cursorCreatedAt
              or (t.createdAt = :cursorCreatedAt and t.id < :cursorId)
          )
        order by t.createdAt desc, t.id desc
    """)
    Slice<TaskEntity> findNextByProjectIdAndAssigneeIdAfterCursor(
            @Param("projectId") Long projectId,
            @Param("assigneeId") Long assigneeId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    public void deleteByProject_Id(Long projectId);

    @Query("SELECT t.id FROM TaskEntity t WHERE t.project.id = :projectId")
    public List<Long> findTaskIdsByProject_Id(@Param("projectId") Long projectId);
}
