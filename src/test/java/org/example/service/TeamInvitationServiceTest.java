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
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamInvitationServiceTest {

    private static final int COOLDOWN_MINUTES = 15;

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
    void acceptInvitation_shouldAddUserToTeam_andInvalidateCache() {
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
                .teamName(team.getName())
                .invitedUserId(userId)
                .invitedByUserId(userId)
                .status(InvitationStatus.ACCEPTED)
                .createdAt(invitation.getCreatedAt())
                .build();

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamInvitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)).thenReturn(false);
        when(teamInvitationRepository.save(any(TeamInvitationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
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
        verify(teamInvitationMapper).toDto(any(TeamInvitationEntity.class));
    }

    @Test
    void acceptInvitation_shouldThrowForbidden_whenInvitationBelongsToAnotherUser() {
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

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> teamInvitationService.acceptInvitation(invitationId)
        );

        assertEquals("You cannot accept this invitation", exception.getMessage());

        verify(teamMemberRepository, never()).save(any(TeamMemberEntity.class));
        verify(teamInvitationRepository, never()).save(any(TeamInvitationEntity.class));
        verify(cacheInvalidationService, never()).evictTeamRelatedCaches(any());
    }

    @Test
    void acceptInvitation_shouldThrowIllegalArgumentException_whenUserIsAlreadyTeamMember() {
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

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> teamInvitationService.acceptInvitation(invitationId)
        );

        assertEquals("User is already a member of the team", exception.getMessage());

        verify(teamMemberRepository, never()).save(any(TeamMemberEntity.class));
        verify(teamInvitationRepository, never()).save(any(TeamInvitationEntity.class));
        verify(cacheInvalidationService, never()).evictTeamRelatedCaches(any());
    }

    @Test
    void inviteUserToTeam_shouldThrowIllegalArgumentException_whenUserIsAlreadyTeamMember() {
        Long invitedUserId = 100L;
        Long userId = 10L;
        Long teamId = 200L;
        Long memberId = 300L;

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

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> teamInvitationService.inviteUserToTeam(dto)
        );

        assertEquals("The invited user is already a member of the team", exception.getMessage());

        verify(teamMemberRepository, never()).save(any(TeamMemberEntity.class));
        verify(teamInvitationRepository, never()).save(any(TeamInvitationEntity.class));
        verify(teamInvitationMapper, never()).toDto(any());
        verify(cacheInvalidationService, never()).evictTeamRelatedCaches(any());
    }

    @Test
    void inviteUserToTeam_shouldCreateInvitation_whenCurrentUserIsOwner() {
        Long invitedUserId = 100L;
        Long userId = 10L;
        Long teamId = 200L;
        Long memberId = 300L;
        Long invitationId = 1L;

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

        TeamInvitationResponseDto response = TeamInvitationResponseDto.builder()
                .id(invitationId)
                .teamName(team.getName())
                .teamId(teamId)
                .invitedUserId(invitedUserId)
                .invitedByUserId(userId)
                .status(InvitationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        TeamInvitationRequestDto dto = new TeamInvitationRequestDto(teamId, invitedUserId);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamRepository.findById(dto.getTeamId())).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(member));
        when(userRepository.findById(dto.getInvitedUserId())).thenReturn(Optional.of(invitedUser));
        when(teamMemberRepository.existsByTeamIdAndUserId(teamId, invitedUserId)).thenReturn(false);
        when(teamInvitationRepository.findTopByTeamIdAndInvitedUserIdOrderByCreatedAtDesc(teamId, invitedUserId))
                .thenReturn(Optional.empty());
        when(teamInvitationRepository.save(any(TeamInvitationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(teamInvitationMapper.toDto(any(TeamInvitationEntity.class))).thenReturn(response);

        TeamInvitationResponseDto result = teamInvitationService.inviteUserToTeam(dto);

        assertNotNull(result);
        assertEquals(teamId, result.getTeamId());
        assertEquals(invitedUserId, result.getInvitedUserId());
        assertEquals(userId, result.getInvitedByUserId());
        assertEquals(InvitationStatus.PENDING, result.getStatus());

        ArgumentCaptor<TeamInvitationEntity> invitationCaptor = ArgumentCaptor.forClass(TeamInvitationEntity.class);
        verify(teamInvitationRepository).save(invitationCaptor.capture());

        TeamInvitationEntity savedInvitation = invitationCaptor.getValue();

        assertEquals(team, savedInvitation.getTeam());
        assertEquals(invitedUser, savedInvitation.getInvitedUser());
        assertEquals(currentUser, savedInvitation.getInvitedBy());
        assertEquals(InvitationStatus.PENDING, savedInvitation.getStatus());

        verify(teamMemberRepository, never()).save(any(TeamMemberEntity.class));
        verify(cacheInvalidationService, never()).evictTeamRelatedCaches(any());
        verify(teamInvitationMapper).toDto(any(TeamInvitationEntity.class));
    }

    @Test
    void inviteUserToTeam_shouldThrowForbidden_whenCurrentUserHasMemberRole() {
        Long invitedUserId = 100L;
        Long userId = 10L;
        Long teamId = 200L;
        Long memberId = 300L;

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
                .role(TeamRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();

        Set<TeamRole> allowedRoles = Set.of(TeamRole.OWNER, TeamRole.MANAGER);

        TeamInvitationRequestDto dto = new TeamInvitationRequestDto(teamId, invitedUserId);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamRepository.findById(dto.getTeamId())).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(member));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> teamInvitationService.inviteUserToTeam(dto)
        );

        assertEquals("You don't have permission to invite members. Required roles: " + allowedRoles, exception.getMessage());

        verify(teamMemberRepository, never()).save(any(TeamMemberEntity.class));
        verify(teamInvitationRepository, never()).save(any(TeamInvitationEntity.class));
        verify(userRepository, never()).findById(anyLong());
        verify(cacheInvalidationService, never()).evictTeamRelatedCaches(any());
    }

    @Test
    void declineInvitation_shouldSetStatusDeclined_whenInvitationBelongsToCurrentUser() {
        Long invitationId = 100L;
        Long userId = 100L;
        Long teamId = 200L;

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

        TeamInvitationResponseDto response = TeamInvitationResponseDto.builder()
                .id(invitationId)
                .teamName(team.getName())
                .teamId(teamId)
                .invitedUserId(userId)
                .invitedByUserId(userId)
                .status(InvitationStatus.DECLINED)
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamInvitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(teamInvitationRepository.save(any(TeamInvitationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(teamInvitationMapper.toDto(any(TeamInvitationEntity.class))).thenReturn(response);

        TeamInvitationResponseDto result = teamInvitationService.declineInvitation(invitationId);

        assertNotNull(result);
        assertEquals(InvitationStatus.DECLINED, invitation.getStatus());

        ArgumentCaptor<TeamInvitationEntity> invitationCaptor = ArgumentCaptor.forClass(TeamInvitationEntity.class);
        verify(teamInvitationRepository).save(invitationCaptor.capture());

        TeamInvitationEntity savedInvitation = invitationCaptor.getValue();
        assertEquals(InvitationStatus.DECLINED, savedInvitation.getStatus());

        verify(teamMemberRepository, never()).save(any(TeamMemberEntity.class));
        verify(cacheInvalidationService, never()).evictTeamRelatedCaches(any());
        verify(teamInvitationMapper).toDto(any(TeamInvitationEntity.class));
    }

    @Test
    void cancelInvitation_shouldSetStatusCancelled_whenCurrentUserIsSender() {
        Long invitationId = 100L;
        Long userId = 10L;
        Long teamId = 200L;
        Long invitedUserId = 100L;

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
                .id(invitedUserId)
                .publicUid("1fas2er1vb")
                .email("test1@mail.ru")
                .password("testpassword1")
                .firstName("Test1")
                .lastName("Test1")
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

        TeamInvitationResponseDto response = TeamInvitationResponseDto.builder()
                .id(invitationId)
                .teamName(team.getName())
                .teamId(teamId)
                .invitedUserId(invitedUserId)
                .invitedByUserId(userId)
                .status(InvitationStatus.CANCELLED)
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamInvitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(teamInvitationRepository.save(any(TeamInvitationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(teamInvitationMapper.toDto(any(TeamInvitationEntity.class))).thenReturn(response);

        TeamInvitationResponseDto result = teamInvitationService.cancelInvitation(invitationId);

        assertNotNull(result);
        assertEquals(InvitationStatus.CANCELLED, invitation.getStatus());

        ArgumentCaptor<TeamInvitationEntity> invitationCaptor = ArgumentCaptor.forClass(TeamInvitationEntity.class);
        verify(teamInvitationRepository).save(invitationCaptor.capture());

        TeamInvitationEntity savedInvitation = invitationCaptor.getValue();
        assertEquals(InvitationStatus.CANCELLED, savedInvitation.getStatus());

        verify(teamMemberRepository, never()).save(any(TeamMemberEntity.class));
        verify(cacheInvalidationService, never()).evictTeamRelatedCaches(any());
        verify(teamInvitationMapper).toDto(any(TeamInvitationEntity.class));
    }

    @Test
    void inviteUserToTeam_shouldThrowIllegalArgumentException_whenInvitingSelf() {
        Long userId = 10L;
        Long invitedUserId = 10L;
        Long teamId = 200L;
        Long memberId = 300L;

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

        TeamInvitationRequestDto dto = new TeamInvitationRequestDto(teamId, invitedUserId);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamRepository.findById(dto.getTeamId())).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(member));
        when(userRepository.findById(dto.getInvitedUserId())).thenReturn(Optional.of(currentUser));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> teamInvitationService.inviteUserToTeam(dto)
        );

        assertEquals("You cannot invite yourself to the team", exception.getMessage());

        verify(teamMemberRepository, never()).save(any(TeamMemberEntity.class));
        verify(teamInvitationRepository, never()).save(any(TeamInvitationEntity.class));
        verify(cacheInvalidationService, never()).evictTeamRelatedCaches(any());
    }

    @Test
    void inviteUserToTeam_shouldThrowIllegalArgumentException_whenPendingInvitationAlreadyExists() {
        Long userId = 10L;
        Long invitedUserId = 20L;
        Long teamId = 200L;

        UserEntity currentUser = UserEntity.builder()
                .id(userId)
                .publicUid("current-user-uid")
                .email("current@test.ru")
                .password("password")
                .firstName("Current")
                .lastName("User")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(teamId)
                .name("Tested Team")
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .team(team)
                .user(currentUser)
                .role(TeamRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();

        UserEntity invitedUser = UserEntity.builder()
                .id(invitedUserId)
                .publicUid("invited-user-uid")
                .email("invited@test.ru")
                .password("password")
                .firstName("Invited")
                .lastName("User")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamInvitationEntity existingInvitation = TeamInvitationEntity.builder()
                .id(777L)
                .team(team)
                .invitedUser(invitedUser)
                .invitedBy(currentUser)
                .status(InvitationStatus.PENDING)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();

        TeamInvitationRequestDto dto = new TeamInvitationRequestDto(teamId, invitedUserId);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(membership));
        when(userRepository.findById(invitedUserId)).thenReturn(Optional.of(invitedUser));
        when(teamMemberRepository.existsByTeamIdAndUserId(teamId, invitedUserId)).thenReturn(false);
        when(teamInvitationRepository.findTopByTeamIdAndInvitedUserIdOrderByCreatedAtDesc(teamId, invitedUserId))
                .thenReturn(Optional.of(existingInvitation));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> teamInvitationService.inviteUserToTeam(dto)
        );

        assertEquals("User already has a pending invitation to this team", exception.getMessage());

        verify(teamInvitationRepository, never()).save(any(TeamInvitationEntity.class));
        verify(cacheInvalidationService, never()).evictTeamRelatedCaches(any());
    }

    @Test
    void inviteUserToTeam_shouldThrowIllegalArgumentException_whenCooldownHasNotExpired() {
        Long userId = 10L;
        Long invitedUserId = 20L;
        Long teamId = 200L;

        UserEntity currentUser = UserEntity.builder()
                .id(userId)
                .publicUid("current-user-uid")
                .email("current@test.ru")
                .password("password")
                .firstName("Current")
                .lastName("User")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(teamId)
                .name("Tested Team")
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .team(team)
                .user(currentUser)
                .role(TeamRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();

        UserEntity invitedUser = UserEntity.builder()
                .id(invitedUserId)
                .publicUid("invited-user-uid")
                .email("invited@test.ru")
                .password("password")
                .firstName("Invited")
                .lastName("User")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamInvitationEntity existingInvitation = TeamInvitationEntity.builder()
                .id(777L)
                .team(team)
                .invitedUser(invitedUser)
                .invitedBy(currentUser)
                .status(InvitationStatus.CANCELLED)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();

        long minutesSinceLastInvite = ChronoUnit.MINUTES.between(existingInvitation.getCreatedAt(), LocalDateTime.now());
        String expectedMessage = String.format(
                "You can invite this user again after %d minutes. Last invitation was %d minutes ago (status: %s)",
                COOLDOWN_MINUTES,
                minutesSinceLastInvite,
                existingInvitation.getStatus()
        );

        TeamInvitationRequestDto dto = new TeamInvitationRequestDto(teamId, invitedUserId);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(membership));
        when(userRepository.findById(invitedUserId)).thenReturn(Optional.of(invitedUser));
        when(teamMemberRepository.existsByTeamIdAndUserId(teamId, invitedUserId)).thenReturn(false);
        when(teamInvitationRepository.findTopByTeamIdAndInvitedUserIdOrderByCreatedAtDesc(teamId, invitedUserId))
                .thenReturn(Optional.of(existingInvitation));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> teamInvitationService.inviteUserToTeam(dto)
        );

        assertEquals(expectedMessage, exception.getMessage());

        verify(teamInvitationRepository, never()).save(any(TeamInvitationEntity.class));
        verify(cacheInvalidationService, never()).evictTeamRelatedCaches(any());
    }
}