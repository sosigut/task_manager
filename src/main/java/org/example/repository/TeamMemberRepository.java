package org.example.repository;

import org.example.entity.TeamMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, Long> {
    Optional<TeamMemberEntity> findByTeamIdAndUserId(Long teamId, Long userId);
    boolean existsByTeamIdAndUserId(Long teamId, Long userId);
    List<TeamMemberEntity> findAllByUserId(Long userId);
    List<TeamMemberEntity> findAllByTeamId(Long teamId);
}
