package org.example.service;

import org.example.entity.*;
import org.example.exception.ForbiddenException;
import org.example.repository.TeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamAccessServiceTest {

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @InjectMocks
    private TeamAccessService teamAccessService;

    @Test
    void shouldThrowForbiddenException_whenUserIsNotInTeam() {

        UserEntity user = UserEntity.builder()
                .email("test@mail.ru")
                .id(1L)
                .build();

        TeamEntity team = TeamEntity.builder()
                .name("test")
                .id(1L)
                .build();

        when(teamMemberRepository.findByTeamIdAndUserId(team.getId(), user.getId()))
                .thenReturn(Optional.empty());

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> teamAccessService.getMembershipOrThrow(team, user));

        assertEquals("User test@mail.ru is not a member of team test",
                exception.getMessage());

        verify(teamMemberRepository).findByTeamIdAndUserId(team.getId(), user.getId());

    }

    @Test
    void shouldThrowForbiddenException_whenUserHasInsufficientRole(){

        UserEntity user = UserEntity.builder()
                .email("test@mail.ru")
                .id(1L)
                .build();

        TeamEntity team = TeamEntity.builder()
                .name("test")
                .id(1L)
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .team(team)
                .user(user)
                .role(TeamRole.MEMBER)
                .build();

        when(teamMemberRepository.findByTeamIdAndUserId(team.getId(), user.getId()))
                .thenReturn(Optional.of(membership));

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> teamAccessService.checkMembershipRole(team, user, Set.of(TeamRole.OWNER, TeamRole.MANAGER)));

        org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                .contains("Недостаточно прав")
                .contains("MEMBER")
                .contains("OWNER")
                .contains("MANAGER");

        verify(teamMemberRepository).findByTeamIdAndUserId(team.getId(), user.getId());

    }

    @Test
    void shouldReturnMembership_whenUserHasSufficientRole(){

        UserEntity user = UserEntity.builder()
                .email("test@mail.ru")
                .id(1L)
                .build();

        TeamEntity team = TeamEntity.builder()
                .name("test")
                .id(1L)
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .team(team)
                .user(user)
                .role(TeamRole.OWNER)
                .build();

        when(teamMemberRepository.findByTeamIdAndUserId(team.getId(), user.getId()))
                .thenReturn(Optional.of(membership));

        TeamMemberEntity result = teamAccessService
                .checkMembershipRole(team, user, Set.of(TeamRole.OWNER, TeamRole.MANAGER));

        assertNotNull(result);
        assertEquals(membership, result);

    }
}