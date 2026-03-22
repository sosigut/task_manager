package org.example.repository;

import org.example.entity.TeamMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, Long> {
}
