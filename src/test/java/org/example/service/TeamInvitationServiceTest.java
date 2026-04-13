package org.example.service;

import org.example.config.cache.CacheInvalidationService;
import org.example.dto.TeamInvitationRequestDto;
import org.example.dto.TeamInvitationResponseDto;
import org.example.entity.*;
import org.example.exception.ForbiddenException;
import org.example.mapper.TeamInvitationMapper;
import org.example.repository.TeamInvitationRepository;
import org.example.repository.TeamMemberRepository;
import org.example.repository.TeamRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamInvitationServiceTest {

    @Mock
    private TeamInvitationRepository teamInvitationRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamInvitationMapper teamInvitationMapper;

    @Mock
    private CacheInvalidationService cacheInvalidationService;

    @InjectMocks
    private TeamInvitationService teamInvitationService;

    @Test
    void acceptInvitation_shouldAddUserToTeam_andInvalidateCache(){

        Long invitationId = 1L;
        Long userId = 10L;
        Long teamId = 100L;

        UserEntity currentUser = UserEntity.builder()
                .id(userId)
                .publicUid("1fasder1vb")
                .email("test@mail.ru")
                .password("testpassword")
                .firstName("Test")
                .lastName("Test")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(teamId)
                .name("Tested Team")
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .build();

        TeamInvitationEntity invitation = TeamInvitationEntity.builder()
                .id(invitationId)
                .team(team)
                .invitedUser(currentUser)
                .invitedBy(currentUser)
                .status(InvitationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        TeamInvitationResponseDto responseDto = TeamInvitationResponseDto.builder()
                .id(invitationId)
                .teamId(teamId)
                .teamName("Backend Team")
                .invitedUserId(userId)
                .invitedByUserId(userId)
                .status(InvitationStatus.ACCEPTED)
                .createdAt(invitation.getCreatedAt())
                .build();

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamInvitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)).thenReturn(false);
        when(teamInvitationRepository.save(any(TeamInvitationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(teamInvitationMapper.toDto(any(TeamInvitationEntity.class))).thenReturn(responseDto);

        TeamInvitationResponseDto result = teamInvitationService.acceptInvitation(invitationId);

        assertNotNull(result);
        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());

        ArgumentCaptor<TeamMemberEntity> memberCaptor = ArgumentCaptor.forClass(TeamMemberEntity.class);
        verify(teamMemberRepository).save(memberCaptor.capture());

        TeamMemberEntity savedMember = memberCaptor.getValue();
        assertEquals(teamId, savedMember.getTeam().getId());
        assertEquals(userId, savedMember.getUser().getId());
        assertEquals(TeamRole.MEMBER, savedMember.getRole());
        assertNotNull(savedMember.getJoinedAt());

        verify(teamInvitationRepository).save(invitation);
        verify(cacheInvalidationService).evictTeamRelatedCaches(teamId);

    }

    @Test
    void acceptInvitation_shouldThrowForbidden_whenInvitationBelongsToAnotherUser(){

        Long invitationId = 1L;
        Long userId = 10L;
        Long anotherUserId = 100L;
        Long teamId = 100L;

        UserEntity currentUser = UserEntity.builder()
                .id(userId)
                .publicUid("1fasder1vb")
                .email("test@mail.ru")
                .password("testpassword")
                .firstName("Test")
                .lastName("Test")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        UserEntity invitedUser = UserEntity.builder()
                .id(anotherUserId)
                .publicUid("2123Vdsvfg")
                .email("another@mail.ru")
                .password("anotherpassword")
                .firstName("Another")
                .lastName("Another")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(teamId)
                .name("Tested Team")
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .build();

        TeamInvitationEntity invitation = TeamInvitationEntity.builder()
                .id(invitationId)
                .team(team)
                .invitedUser(invitedUser)
                .invitedBy(currentUser)
                .status(InvitationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamInvitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));

        ForbiddenException exception = assertThrows(ForbiddenException.class, () -> {
            teamInvitationService.acceptInvitation(invitationId);
        });

        assertEquals("You cannot accept this invitation", exception.getMessage());

        verify(teamMemberRepository, never()).save(any(TeamMemberEntity.class));
        verify(teamInvitationRepository, never()).save(any(TeamInvitationEntity.class));
        verify(cacheInvalidationService, never()).evictTeamRelatedCaches(any());

    }

    @Test
    void acceptInvitation_shouldThrowForbidden_whenCurrentUserIsMember() {
        Long invitationId = 1L;
        Long userId = 10L;
        Long teamId = 100L;
        UserEntity currentUser = UserEntity.builder()
                .id(userId)
                .publicUid("1fasder1vb")
                .email("test@mail.ru")
                .password("testpassword")
                .firstName("Test")
                .lastName("Test")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(teamId)
                .name("Tested Team")
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .build();

        TeamInvitationEntity invitation = TeamInvitationEntity.builder()
                .id(invitationId)
                .team(team)
                .invitedUser(currentUser)
                .invitedBy(currentUser)
                .status(InvitationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamInvitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> { teamInvitationService.acceptInvitation(invitationId);
        });

        assertEquals("User is already a member of the team", exception.getMessage());

        verify(teamMemberRepository, never()).save(any(TeamMemberEntity.class));
        verify(teamInvitationRepository, never()).save(any(TeamInvitationEntity.class));
        verify(cacheInvalidationService, never()).evictTeamRelatedCaches(any());
    }

    @Test
    void inviteUserToTeam_shouldThrowIllegalArgumentException_whenUserIsAlreadyTeamMember(){

        Long invitedUserId = 100L;
        Long userId = 10L;
        Long teamId = 100L;
        Long memberId = 100L;

        UserEntity currentUser = UserEntity.builder()
                .id(userId)
                .publicUid("1fasder1vb")
                .email("test@mail.ru")
                .password("testpassword")
                .firstName("Test")
                .lastName("Test")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(teamId)
                .name("Tested Team")
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .build();

        TeamMemberEntity member = TeamMemberEntity.builder()
                .id(memberId)
                .team(team)
                .user(currentUser)
                .role(TeamRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();

        UserEntity invitedUser = UserEntity.builder()
                .id(invitedUserId)
                .publicUid("invited-user-uid")
                .email("invited@mail.ru")
                .firstName("Invited")
                .lastName("User")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamInvitationRequestDto dto = new TeamInvitationRequestDto(teamId, invitedUserId);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(member));
        when(userRepository.findById(invitedUserId)).thenReturn(Optional.of(invitedUser));
        when(teamMemberRepository.existsByTeamIdAndUserId(teamId, invitedUserId)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> { teamInvitationService.inviteUserToTeam(dto);
                });

        assertEquals("The invited user is already a member of the team", exception.getMessage());

        verify(teamMemberRepository, never()).save(any(TeamMemberEntity.class));
        verify(teamInvitationRepository, never()).save(any(TeamInvitationEntity.class));
        verify(cacheInvalidationService, never()).evictTeamRelatedCaches(any());
    }

}
