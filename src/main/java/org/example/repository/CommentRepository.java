package org.example.repository;

import org.example.entity.CommentEntity;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface CommentRepository extends JpaRepository<CommentEntity,Long> {

    @Query("""
        select comment from CommentEntity comment
        where comment.task.id = :taskId
        order by comment.createdAt desc, comment.id desc
    """)
    public Slice<CommentEntity> findFirstPageByTaskIdOrderByCreatedAtAndIdDesc(@Param("taskId") Long taskId,
                                                                         Pageable pageable);

    @Query("""
        select comment from CommentEntity comment
        where comment.task.id = :taskId
          and (
              comment.createdAt < :cursorCreatedAt
              or (comment.createdAt = :cursorCreatedAt and comment.id < :cursorId)
          )
        order by comment.createdAt desc, comment.id desc
    """)
    public Slice<CommentEntity> findNextPageByTaskIdOrderByCreatedAtAndIdDescAfterCursor(
            @Param("taskId") Long taskId,
            @Param("cursorCreatedAt")LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    public void deleteByTask_Id(Long taskId);
    public void deleteByTask_ProjectId(Long projectId);

}
