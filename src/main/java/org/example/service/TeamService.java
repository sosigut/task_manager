package org.example.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.dto.CreateTeamRequestDto;
import org.example.dto.TeamResponseDto;
import org.example.entity.TeamEntity;
import org.example.entity.TeamMemberEntity;
import org.example.entity.TeamRole;
import org.example.entity.UserEntity;
import org.example.mapper.TeamMapper;
import org.example.repository.TeamMemberRepository;
import org.example.repository.TeamRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final UserService userService;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamMapper teamMapper;

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public TeamResponseDto createTeam(CreateTeamRequestDto dto){

        UserEntity currentUser = userService.getCurrentUser();
        String trimmedTeamName = dto.getTeamName().trim();

        if(trimmedTeamName.isEmpty()){
            throw new IllegalArgumentException("Название команды не должно быть пусты");
        }

        if(trimmedTeamName.length() > 100){
            throw new IllegalArgumentException("Название команды не должно превышать 100 символов");
        }

        TeamEntity team = TeamEntity.builder()
                .name(trimmedTeamName)
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity savedTeam = teamRepository.save(team);

        TeamMemberEntity teamMember = TeamMemberEntity.builder()
                .team(savedTeam)
                .user(currentUser)
                .role(TeamRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();

        teamMemberRepository.save(teamMember);

        return teamMapper.toDto(savedTeam);
    }
}
