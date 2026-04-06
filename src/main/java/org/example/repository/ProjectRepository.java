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
        where project.team.id in :teamIds
        order by project.createdAt desc, project.id desc
    """)
    public Slice<ProjectEntity> findFirstPageByCreatedAtAndTeamIdsDesc(@Param("teamIds") List<Long> teamIds,
                                                                       Pageable pageable);

    @Query("""
        select project from ProjectEntity project
        where project.team.id in :teamIds
          and (
              project.createdAt < :cursorCreatedAt
              or (project.createdAt = :cursorCreatedAt and project.id < :cursorId)
          )
        order by project.createdAt desc, project.id desc
    """)
    public Slice<ProjectEntity> findNextPageByCreatedAtAndTeamIdsAfterCursor(
            @Param("teamIds") List<Long> teamIds,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
