package org.example.repository;

import org.example.entity.ProjectEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    @Query("""
        select project from ProjectEntity project
        where project.owner.id = :ownerId
        order by project.createdAt desc, project.id desc
    """)
    public Slice<ProjectEntity> findFirstPageByCreatedAtAndOwnerIdDesc(@Param("ownerId") Long ownerId,
                                                                       Pageable pageable);

    @Query("""
        select project from ProjectEntity project
        where project.owner.id = :ownerId
          and (
              project.createdAt < :cursorCreatedAt
              or (project.createdAt = :cursorCreatedAt and project.id < :cursorId)
          )
        order by project.createdAt desc, project.id desc
    """)
    public Slice<ProjectEntity> findNextPageByCreatedAtAndOwnerIdAfterCursor(@Param("ownerId") Long ownerId,
                                                                             @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
                                                                             @Param("cursorId") Long cursorId,
                                                                             Pageable pageable);
}
