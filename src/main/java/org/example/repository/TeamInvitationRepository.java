package org.example.repository;

import org.example.entity.InvitationStatus;
import org.example.entity.TeamInvitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamInvitationRepository extends JpaRepository<TeamInvitationEntity,Long> {
    boolean existsByTeamIdAndInvitedUserIdAndStatus(Long teamId, Long invitedUserId, InvitationStatus status);

    Optional<TeamInvitationEntity> findByTeamIdAndInvitedUserIdAndStatus(
            Long teamId,
            Long invitedUserId,
            InvitationStatus status
    );

    List<TeamInvitationEntity> findAllByInvitedUserId(Long invitedUserId);

    List<TeamInvitationEntity> findAllByInvitedUserIdAndStatus(
            Long invitedUserId,
            InvitationStatus status
    );
}
