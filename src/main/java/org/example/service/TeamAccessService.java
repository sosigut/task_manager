package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entity.TeamEntity;
import org.example.entity.TeamMemberEntity;
import org.example.entity.TeamRole;
import org.example.entity.UserEntity;
import org.example.exception.ForbiddenException;
import org.example.repository.TeamMemberRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class TeamAccessService {

    private final TeamMemberRepository teamMemberRepository;

    public TeamMemberEntity checkMembershipRole(TeamEntity team, UserEntity user, Set<TeamRole> allowedRoles) {
        TeamMemberEntity membership = getMembershipOrThrow(team, user);

        if (allowedRoles != null && !allowedRoles.isEmpty()) {
            if (!allowedRoles.contains(membership.getRole())) {
                throw new ForbiddenException(
                        String.format("Недостаточно прав. User role: %s, Required roles: %s",
                                membership.getRole(), allowedRoles)
                );
            }
        }

        return membership;
    }

    public TeamMemberEntity getMembershipOrThrow(TeamEntity team, UserEntity user) {
        return teamMemberRepository
                .findByTeamIdAndUserId(team.getId(), user.getId())
                .orElseThrow(() -> new ForbiddenException(
                        String.format("User %s is not a member of team %s",
                                user.getUsername(), team.getName())
                ));
    }

    public TeamMemberEntity checkMembership(TeamEntity team, UserEntity user) {
        return checkMembershipRole(team, user, null);
    }

}
