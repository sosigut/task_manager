package org.example.mapper;

import org.example.dto.TeamMemberResponseDto;
import org.example.entity.TeamMemberEntity;
import org.springframework.stereotype.Component;

@Component
public class TeamMemberMapper {

    public TeamMemberResponseDto toDto (TeamMemberEntity entity) {

        if(entity == null) return null;

        return TeamMemberResponseDto.builder()
                .id(entity.getUser().getId())
                .publicUid(entity.getUser().getPublicUid())
                .firstName(entity.getUser().getFirstName())
                .lastName(entity.getUser().getLastName())
                .teamRole(entity.getRole())
                .joinedAt(entity.getJoinedAt())
                .build();
    }

}
