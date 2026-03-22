package org.example.repository;

import org.example.entity.TeamMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, Long> {
    TeamMemberEntity findByTeamIdAndUserId(Long teamId, Long userId);
    boolean existsByTeamIdAndUserId(Long teamId, Long userId);
    List<TeamMemberEntity> findAllByUserId(Long userId);
    List<TeamMemberEntity> findAllByTeamId(Long teamId);
}
