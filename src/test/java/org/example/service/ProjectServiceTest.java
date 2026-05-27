package org.example.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.config.cache.CacheInvalidationService;
import org.example.dto.CreateProjectRequestDto;
import org.example.dto.ProjectResponseDto;
import org.example.dto.UpdateProjectRequestDto;
import org.example.entity.*;
import org.example.exception.ForbiddenException;
import org.example.exception.NotFoundException;
import org.example.mapper.ProjectMapper;
import org.example.pagination.KeysetPageBuilder;
import org.example.pagination.KeysetPageResponseDto;
import org.example.pagination.KeysetPaginationFetcher;
import org.example.pagination.KeysetPaginationUtils;
import org.example.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private UserService userService;

    @Mock
    private TeamAccessService teamAccessService;

    @Mock
    private ProjectRepository projectRepository;

    @Spy
    private KeysetPageBuilder keysetPageBuilder;

    @Spy
    private KeysetPaginationUtils keysetPaginationUtils;

    @Spy
    private KeysetPaginationFetcher keysetPaginationFetcher;

    @Mock
    private CacheInvalidationService cacheInvalidationService;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private TaskHistoryRepository taskHistoryRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    private ProjectService projectService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();

        projectService = new ProjectService(
                projectMapper,
                userService,
                teamAccessService,
                projectRepository,
                keysetPageBuilder,
                keysetPaginationUtils,
                keysetPaginationFetcher,
                cacheInvalidationService,
                taskRepository,
                commentRepository,
                taskHistoryRepository,
                meterRegistry,
                teamRepository,
                teamMemberRepository
        );

        projectService.initMetrics();
    }

    @Test
    void createProject_shouldCreateProject_whenUserIsOwner() {

        Long userId = 10L;
        Long teamId = 100L;
        Long projectId = 999L;

        UserEntity currentUser = UserEntity.builder()
                .id(userId)
                .publicUid("user-uid")
                .email("test@mail.com")
                .password("pass")
                .firstName("Test")
                .lastName("User")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(teamId)
                .name("Backend Team")
                .createdBy(currentUser)
                .createdAt(LocalDateTime.now())
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .team(team)
                .user(currentUser)
                .role(TeamRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();

        CreateProjectRequestDto dto = new CreateProjectRequestDto(
                "New Project",
                "Project Description",
                teamId
        );

        ProjectEntity projectEntity = ProjectEntity.builder()
                .name("New Project")
                .description("Project Description")
                .owner(currentUser)
                .team(team)
                .createdAt(LocalDateTime.now())
                .build();

        ProjectEntity savedProject = ProjectEntity.builder()
                .id(projectId)
                .name("New Project")
                .description("Project Description")
                .owner(currentUser)
                .team(team)
                .createdAt(LocalDateTime.now())
                .build();

        ProjectResponseDto responseDto = ProjectResponseDto.builder()
                .id(projectId)
                .name("New Project")
                .description("Project Description")
                .ownerId(userId)
                .teamId(teamId)
                .createdAt(savedProject.getCreatedAt())
                .build();

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        when(teamAccessService.checkMembershipRole(team, currentUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER)))
                .thenReturn(membership);

        when(projectMapper.toEntity(dto, currentUser, team)).thenReturn(projectEntity);
        when(projectRepository.save(projectEntity)).thenReturn(savedProject);
        when(projectMapper.toDto(savedProject)).thenReturn(responseDto);

        ProjectResponseDto result = projectService.createProject(dto, teamId);

        assertNotNull(result);
        assertEquals(projectId, result.getId());
        assertEquals("New Project", result.getName());
        assertEquals("Project Description", result.getDescription());
        assertEquals(teamId, result.getTeamId());
        assertEquals(userId, result.getOwnerId());

        verify(projectRepository).save(projectEntity);
        verify(cacheInvalidationService).evictProjectPagesForAllTeamMembers(teamId);
    }

    @Test
    void createProject_shouldThrowForbidden_whenUserIsMember() {

        Long userId = 10L;
        Long teamId = 100L;

        UserEntity currentUser = UserEntity.builder()
                .id(userId)
                .publicUid("user-uid")
                .email("member@mail.com")
                .firstName("Simple")
                .lastName("Member")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(teamId)
                .name("Backend Team")
                .createdBy(UserEntity.builder().id(99L).build())
                .createdAt(LocalDateTime.now())
                .build();

        CreateProjectRequestDto dto = new CreateProjectRequestDto(
                "New Project",
                "Project Description",
                teamId
        );

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        doThrow(new ForbiddenException(
                "Недостаточно прав. User role: MEMBER, Required roles: [OWNER, MANAGER]"
        )).when(teamAccessService).checkMembershipRole(
                team,
                currentUser,
                Set.of(TeamRole.OWNER, TeamRole.MANAGER)
        );
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> projectService.createProject(dto, teamId));

        org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                .contains("Недостаточно прав")
                .contains("MEMBER")
                .contains("OWNER")
                .contains("MANAGER");

        verify(projectRepository, never()).save(any(ProjectEntity.class));
        verify(cacheInvalidationService, never()).evictProjectPagesForAllTeamMembers(any());

    }

    @Test
    void updateProject_shouldUpdateProject_whenUserIsOwner(){

        Long userId = 10L;
        Long teamId = 100L;
        Long projectId = 999L;

        UserEntity currentUser = UserEntity.builder()
                .id(userId)
                .publicUid("user-uid")
                .email("member@mail.com")
                .firstName("Simple")
                .lastName("Member")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(teamId)
                .name("Backend Team")
                .createdBy(UserEntity.builder().id(99L).build())
                .createdAt(LocalDateTime.now())
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .team(team)
                .user(currentUser)
                .role(TeamRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();

        ProjectEntity project = ProjectEntity.builder()
                .id(projectId)
                .name("zazaza")
                .description("zazazazazazaza")
                .owner(currentUser)
                .team(team)
                .createdAt(LocalDateTime.now())
                .build();

        ProjectResponseDto responseDto = ProjectResponseDto.builder()
                .id(projectId)
                .name("aaaaaaaa")
                .description("aaaaaaaaaaaaaaaa")
                .ownerId(userId)
                .teamId(teamId)
                .createdAt(project.getCreatedAt())
                .build();


        UpdateProjectRequestDto dto = new UpdateProjectRequestDto(
                "aaaaaaaa", "aaaaaaaaaaaaaaaa");

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        when(teamAccessService.checkMembershipRole(team, currentUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER)))
                .thenReturn(membership);

        when(projectRepository.save(project)).thenReturn(project);
        when(projectMapper.toDto(project)).thenReturn(responseDto);

        ProjectResponseDto result = projectService.updateProject(dto, projectId);

        assertNotNull(result);
        assertEquals(projectId, result.getId());
        assertEquals("aaaaaaaa", result.getName());
        assertEquals("aaaaaaaaaaaaaaaa", result.getDescription());
        assertEquals(teamId, result.getTeamId());
        assertEquals(userId, result.getOwnerId());

        assertEquals("aaaaaaaa", project.getName());
        assertEquals("aaaaaaaaaaaaaaaa", project.getDescription());

        verify(projectRepository).findById(projectId);
        verify(userService).getCurrentUser();
        verify(teamAccessService).checkMembershipRole(team, currentUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER));
        verify(projectRepository).save(project);
        verify(projectMapper).toDto(project);
        verify(cacheInvalidationService).evictProjectPagesForAllTeamMembers(teamId);

    }

    @Test
    void updateProject_shouldThrowIllegalArgumentException_whenBothFieldsAreNull(){

        Long projectId = 999L;

        UpdateProjectRequestDto dto = new UpdateProjectRequestDto(
                null, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> projectService.updateProject(dto, projectId)
        );

        assertEquals("Нет полей для обновления", exception.getMessage());

        verify(projectRepository, never()).findById(any());
        verify(userService, never()).getCurrentUser();
        verify(teamAccessService, never()).checkMembershipRole(any(), any(), any());
        verify(cacheInvalidationService, never()).evictProjectPagesForAllTeamMembers(any());
        verify(projectRepository, never()).save(any(ProjectEntity.class));

    }

    @Test
    void updateProject_shouldThrowIllegalArgumentException_whenNameIsBlank(){

        Long userId = 10L;
        Long teamId = 100L;
        Long projectId = 999L;

        UserEntity currentUser = UserEntity.builder()
                .id(userId)
                .publicUid("user-uid")
                .email("member@mail.com")
                .firstName("Simple")
                .lastName("Member")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(teamId)
                .name("Backend Team")
                .createdBy(UserEntity.builder().id(99L).build())
                .createdAt(LocalDateTime.now())
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .team(team)
                .user(currentUser)
                .role(TeamRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();

        ProjectEntity project = ProjectEntity.builder()
                .id(projectId)
                .name("zazaza")
                .description("zazazazazazaza")
                .owner(currentUser)
                .team(team)
                .createdAt(LocalDateTime.now())
                .build();

        UpdateProjectRequestDto dto = new UpdateProjectRequestDto(
                " ", "aaaaaaaaaaaaaaaa");

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        when(teamAccessService.checkMembershipRole(team, currentUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER)))
                .thenReturn(membership);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> projectService.updateProject(dto, projectId)
        );

        assertEquals("Название проекта не должно быть пусты", exception.getMessage());

        verify(projectRepository).findById(projectId);
        verify(userService).getCurrentUser();
        verify(teamAccessService).checkMembershipRole(team, currentUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER));
        verify(cacheInvalidationService, never()).evictProjectPagesForAllTeamMembers(any());
        verify(projectRepository, never()).save(any(ProjectEntity.class));

    }

    @Test
    void updateProject_shouldThrowIllegalArgumentException_whenDescriptionIsBlank(){

        Long userId = 10L;
        Long teamId = 100L;
        Long projectId = 999L;

        UserEntity currentUser = UserEntity.builder()
                .id(userId)
                .publicUid("user-uid")
                .email("member@mail.com")
                .firstName("Simple")
                .lastName("Member")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(teamId)
                .name("Backend Team")
                .createdBy(UserEntity.builder().id(99L).build())
                .createdAt(LocalDateTime.now())
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .team(team)
                .user(currentUser)
                .role(TeamRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();

        ProjectEntity project = ProjectEntity.builder()
                .id(projectId)
                .name("zazaza")
                .description("zazazazazazaza")
                .owner(currentUser)
                .team(team)
                .createdAt(LocalDateTime.now())
                .build();

        UpdateProjectRequestDto dto = new UpdateProjectRequestDto(
                "aaaaaaaaaa", " ");

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        when(teamAccessService.checkMembershipRole(team, currentUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER)))
                .thenReturn(membership);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> projectService.updateProject(dto, projectId)
        );

        assertEquals("Описание проекта не должно быть пусты", exception.getMessage());

        verify(projectRepository).findById(projectId);
        verify(userService).getCurrentUser();
        verify(teamAccessService).checkMembershipRole(team, currentUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER));
        verify(cacheInvalidationService, never()).evictProjectPagesForAllTeamMembers(any());
        verify(projectRepository, never()).save(any(ProjectEntity.class));

    }

    @Test
    void updateProject_shouldThrowNotFoundException_whenProjectDoesNotExist(){

        Long projectId = 999L;

        UpdateProjectRequestDto dto = new UpdateProjectRequestDto(
                "aaaaaaaaaa", "aaaaaaaaa");

        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> projectService.updateProject(dto, projectId)
        );

        assertEquals("Project not found", exception.getMessage());

        verify(projectRepository).findById(projectId);
        verify(userService, never()).getCurrentUser();
        verify(teamAccessService, never()).checkMembershipRole(any(), any(), any());
        verify(cacheInvalidationService, never()).evictProjectPagesForAllTeamMembers(any());
        verify(projectRepository, never()).save(any(ProjectEntity.class));

    }

    @Test
    void updateProject_shouldThrowForbiddenException_whenUserIsMember(){

        Long userId = 10L;
        Long teamId = 100L;
        Long projectId = 999L;

        UserEntity currentUser = UserEntity.builder()
                .id(userId)
                .publicUid("user-uid")
                .email("member@mail.com")
                .firstName("Simple")
                .lastName("Member")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(teamId)
                .name("Backend Team")
                .createdBy(UserEntity.builder().id(99L).build())
                .createdAt(LocalDateTime.now())
                .build();

        ProjectEntity project = ProjectEntity.builder()
                .id(projectId)
                .name("zazaza")
                .description("zazazazazazaza")
                .owner(currentUser)
                .team(team)
                .createdAt(LocalDateTime.now())
                .build();

        UpdateProjectRequestDto dto = new UpdateProjectRequestDto(
                "aaaaaaaaaa", "aaaaaaaaaaaaaaa");

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        when(teamAccessService.checkMembershipRole(team, currentUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER)))
                .thenThrow(new ForbiddenException("Недостаточно прав. User role: MEMBER, Required roles: [OWNER, MANAGER]"));

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> projectService.updateProject(dto, projectId));

        org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                .contains("Недостаточно прав")
                .contains("MEMBER")
                .contains("OWNER")
                .contains("MANAGER");

        verify(projectRepository).findById(projectId);
        verify(userService).getCurrentUser();
        verify(teamAccessService).checkMembershipRole(team,
                currentUser,
                Set.of(TeamRole.OWNER, TeamRole.MANAGER));
        verify(cacheInvalidationService, never()).evictProjectPagesForAllTeamMembers(any());
        verify(projectRepository, never()).save(any(ProjectEntity.class));

    }

    @Test
    void deleteProject_shouldDeleteProject_whenUserIsOwner(){

        Long ownerUserId = 10L;
        Long teamId = 100L;
        Long projectId = 999L;
        Long taskId1 = 1L;
        Long taskId2 = 2L;

        UserEntity ownerUser = UserEntity.builder()
                .id(ownerUserId)
                .publicUid("user-uid")
                .email("owner@mail.com")
                .firstName("Simple")
                .lastName("Member")
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(teamId)
                .name("Backend Team")
                .createdBy(UserEntity.builder().id(99L).build())
                .createdAt(LocalDateTime.now())
                .build();

        ProjectEntity project = ProjectEntity.builder()
                .id(projectId)
                .name("zazaza")
                .description("zazazazazazaza")
                .owner(ownerUser)
                .team(team)
                .createdAt(LocalDateTime.now())
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .team(team)
                .user(ownerUser)
                .role(TeamRole.OWNER)
                .joinedAt(LocalDateTime.now())
                .build();

        List<Long> taskIds = List.of(taskId1, taskId2);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(userService.getCurrentUser()).thenReturn(ownerUser);
        when(teamAccessService.checkMembershipRole(team, ownerUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER)))
                .thenReturn(membership);
        when(taskRepository.findTaskIdsByProject_Id(projectId)).thenReturn(taskIds);

        projectService.deleteProject(projectId);

        verify(projectRepository).findById(projectId);
        verify(userService).getCurrentUser();
        verify(teamAccessService).checkMembershipRole(team, ownerUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER));
        verify(taskRepository).findTaskIdsByProject_Id(projectId);

        verify(taskHistoryRepository).deleteByTask_ProjectId(projectId);
        verify(commentRepository).deleteByTask_ProjectId(projectId);

        verify(cacheInvalidationService).evictCommentPagesByTaskId(taskId1);
        verify(cacheInvalidationService).evictCommentPagesByTaskId(taskId2);

        verify(taskRepository).deleteByProject_Id(projectId);
        verify(cacheInvalidationService).evictTaskPagesByProjectId(projectId);
        verify(projectRepository).delete(project);

        verify(cacheInvalidationService).evictProjectPagesForAllTeamMembers(teamId);

    }

    @Test
    void getMyTeamProjects_shouldReturnEmptyPage_whenUserHasNoTeams() {

        UserEntity currentUser = UserEntity.builder().id(1L).build();
        Integer limit = 10;


        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamMemberRepository.findAllByUserId(currentUser.getId()))
                .thenReturn(Collections.emptyList());

        KeysetPageResponseDto<ProjectResponseDto> result =
                projectService.getMyTeamProjects(limit, null, null);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
        assertNull(result.getCursorCreatedAt());
        assertNull(result.getCursorId());
        assertFalse(result.isHasNext());

        verifyNoInteractions(projectRepository);
        verifyNoInteractions(keysetPaginationFetcher);
    }

    @Test
    void getMyTeamProjects_shouldReturnFirstPage_whenCursorsAreNull(){

        UserEntity currentUser = UserEntity.builder()
                .id(1L)
                .build();
        
        TeamEntity team = TeamEntity.builder()
                .id(1L)
                .build();
        
        TeamMemberEntity membership = TeamMemberEntity.builder()
                .id(1L)
                .user(currentUser)
                .team(team)
                .role(TeamRole.OWNER)
                .build();

        List<TeamMemberEntity> memberships = List.of(membership);

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .team(team)
                .build();

        Slice<ProjectEntity> projectSlice = new SliceImpl<>(List.of(project));
        
        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamMemberRepository.findAllByUserId(currentUser.getId()))
                .thenReturn(memberships);

        when(projectRepository.findFirstPageByCreatedAtAndTeamIdsDesc(
                eq(List.of(1L)), any(Pageable.class)))
                .thenReturn(projectSlice);

        KeysetPageResponseDto<ProjectResponseDto> result =
                projectService.getMyTeamProjects(10, null, null);

        assertNotNull(result);
        assertFalse(result.getItems().isEmpty());
        assertEquals(1, result.getItems().size());

        verify(projectRepository).findFirstPageByCreatedAtAndTeamIdsDesc(
                eq(List.of(1L)), any(Pageable.class)
        );
        verify(userService).getCurrentUser();
        
    }

    @Test
    void getMyTeamProjects_shouldReturnNextPage_whenCursorsAreProvided(){

        LocalDateTime cursorDate = LocalDateTime.now();
        Long cursorId = 5L;

        UserEntity currentUser = UserEntity.builder()
                .id(1L)
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(1L)
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .id(1L)
                .user(currentUser)
                .team(team)
                .role(TeamRole.OWNER)
                .build();

        List<TeamMemberEntity> memberships = List.of(membership);

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .team(team)
                .build();

        Slice<ProjectEntity> projectSlice = new SliceImpl<>(List.of(project));

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamMemberRepository.findAllByUserId(currentUser.getId()))
                .thenReturn(memberships);

        when(projectRepository.findNextPageByCreatedAtAndTeamIdsAfterCursor(
                eq(List.of(1L)), eq(cursorDate), eq(cursorId), any(Pageable.class)))
                .thenReturn(projectSlice);

        KeysetPageResponseDto<ProjectResponseDto> result =
                projectService.getMyTeamProjects(10, cursorDate, cursorId);


        assertNotNull(result);
        assertFalse(result.getItems().isEmpty());
        assertEquals(1, result.getItems().size());

        verify(projectRepository).findNextPageByCreatedAtAndTeamIdsAfterCursor(
                eq(List.of(1L)), eq(cursorDate), eq(cursorId), any(Pageable.class));
        verify(userService).getCurrentUser();

    }

}
