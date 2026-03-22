package org.example.service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.dto.CreateTeamRequestDto;
import org.example.dto.TeamMemberResponseDto;
import org.example.dto.TeamResponseDto;
import org.example.entity.TeamEntity;
import org.example.entity.TeamMemberEntity;
import org.example.entity.TeamRole;
import org.example.entity.UserEntity;
import org.example.exception.ForbiddenException;
import org.example.mapper.TeamMapper;
import org.example.mapper.TeamMemberMapper;
import org.example.repository.TeamMemberRepository;
import org.example.repository.TeamRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final UserService userService;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;

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

    public List<TeamResponseDto> getMyTeams(){

        UserEntity currentUser = userService.getCurrentUser();
        List<TeamMemberEntity> members = teamMemberRepository.findAllByUserId(currentUser.getId());
        List<TeamEntity> teams = members.stream().map(TeamMemberEntity::getTeam).toList();

        return teams.stream().map(teamMapper::toDto).collect(Collectors.toList());

    }

    public List<TeamMemberResponseDto> getTeamMembers(Long teamId){

        UserEntity currentUser = userService.getCurrentUser();

        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, currentUser.getId())) {
            throw new ForbiddenException("Access denied: you are not a member of this team");
        }

        List<TeamMemberEntity> members = teamMemberRepository.findAllByTeamId(teamId);

        return members.stream()
                .map(teamMemberMapper::toDto)
                .collect(Collectors.toList());
    }

}
