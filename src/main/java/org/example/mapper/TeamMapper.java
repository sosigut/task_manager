package org.example.mapper;

import org.example.dto.CreateTeamRequestDto;
import org.example.dto.TeamResponseDto;
import org.example.entity.TeamEntity;
import org.example.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TeamMapper {

    public TeamResponseDto toDto (TeamEntity entity) {

        if(entity == null) return null;

        return TeamResponseDto.builder()
                .id(entity.getId())
                .teamName(entity.getName())
                .createdAt(entity.getCreatedAt())
                .createdByUserId(entity.getCreatedBy().getId())
                .createdByUserUid(entity.getCreatedBy().getPublicUid())
                .build();
    }
}
