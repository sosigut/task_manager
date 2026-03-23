package org.example.mapper;

import org.example.dto.TeamInvitationResponseDto;
import org.example.entity.TeamInvitationEntity;
import org.springframework.stereotype.Component;

@Component
public class TeamInvitationMapper {

    public TeamInvitationResponseDto toDto(TeamInvitationEntity entity) {

        if (entity == null) return null;

        return TeamInvitationResponseDto.builder()
                .id(entity.getId())
                .teamName(entity.getTeam().getName())
                .teamId(entity.getTeam().getId())
                .invitedUserId(entity.getInvitedUser().getId())
                .invitedByUserId(entity.getInvitedBy().getId())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
