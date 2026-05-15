package org.example.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.config.cache.CacheInvalidationService;
import org.example.dto.CreateTaskRequestDto;
import org.example.dto.TaskResponseDto;
import org.example.entity.*;
import org.example.exception.ForbiddenException;
import org.example.exception.NotFoundException;
import org.example.mapper.TaskHistoryMapper;
import org.example.mapper.TaskMapper;
import org.example.pagination.KeysetPageBuilder;
import org.example.pagination.KeysetPaginationFetcher;
import org.example.pagination.KeysetPaginationUtils;
import org.example.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private TaskHistoryMapper taskHistoryMapper;

    @Mock
    private TaskHistoryRepository taskHistoryRepository;

    @Mock
    private KeysetPaginationUtils keysetPaginationUtils;

    @Mock
    private KeysetPageBuilder pageBuilder;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private KeysetPaginationFetcher keysetPaginationFetcher;

    @Mock
    private CacheInvalidationService cacheInvalidationService;

    @Mock
    private TeamAccessService teamAccessService;

    private TaskService taskService;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp(){
        meterRegistry = new SimpleMeterRegistry();

        taskService = new TaskService(
                taskRepository,
                userService,
                userRepository,
                projectRepository,
                taskMapper,
                taskHistoryMapper,
                taskHistoryRepository,
                keysetPaginationUtils,
                pageBuilder,
                commentRepository,
                keysetPaginationFetcher,
                cacheInvalidationService,
                meterRegistry,
                teamAccessService
        );

        taskService.initMetrics();
    }

    @Test
    void changeStatus_shouldUpdateStatusAndSaveHistory_whenUserIsAuthorized(){

        UserEntity currentUser = UserEntity.builder()
                .id(1L)
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(1L)
                .build();

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .owner(currentUser)
                .team(team)
                .build();

        TaskEntity taskEntity = TaskEntity.builder()
                .id(1L)
                .status(Status.TODO)
                .project(project)
                .assignee(currentUser)
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .id(1L)
                .team(team)
                .user(currentUser)
                .role(TeamRole.OWNER)
                .build();

        TaskResponseDto responseDto = TaskResponseDto.builder()
                .id(1L)
                .status(Status.IN_PROGRESS)
                .projectId(project.getId())
                .assigneeId(currentUser.getId())
                .build();

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(taskEntity));

        when(teamAccessService.checkMembership(team, currentUser))
                .thenReturn(membership);

        when(taskRepository.save(taskEntity)).thenReturn(taskEntity);
        when(taskMapper.toDto(taskEntity)).thenReturn(responseDto);

        TaskResponseDto result = taskService.changeStatus(1L, Status.IN_PROGRESS);

        assertNotNull(result);
        assertEquals(responseDto, result);
        assertEquals(Status.IN_PROGRESS, result.getStatus());

        verify(taskRepository).save(taskEntity);
        verify(cacheInvalidationService).evictTaskPagesByProjectId(project.getId());
        verify(taskHistoryRepository).save(any(TaskHistoryEntity.class));

    }

    @Test
    void changeStatus_shouldThrowForbiddenException_whenUserLacksPermissions(){

        UserEntity currentUser = UserEntity.builder()
                .id(1L)
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(1L)
                .build();

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .owner(currentUser)
                .team(team)
                .build();

        TaskEntity taskEntity = TaskEntity.builder()
                .id(1L)
                .status(Status.TODO)
                .project(project)
                .assignee(currentUser)
                .build();

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(taskEntity));

        when(teamAccessService.checkMembership(team, currentUser))
                .thenThrow(new ForbiddenException("Недостаточно прав. User role: MEMBER, Required roles: [OWNER, MANAGER]"));

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> taskService.changeStatus(1L, Status.IN_PROGRESS));

        assertEquals("Недостаточно прав. User role: MEMBER, Required roles: [OWNER, MANAGER]",
                exception.getMessage());

        verify(taskRepository, never()).save(any(TaskEntity.class));
        verify(cacheInvalidationService, never()).evictTaskPagesByProjectId(project.getId());
        verify(taskHistoryRepository, never()).save(any(TaskHistoryEntity.class));
        verify(userService).getCurrentUser();
        verify(taskRepository).findById(1L);

    }

    @Test
    void createTask_shouldCreateTask_whenValidData(){

        UserEntity currentUser = UserEntity.builder()
                .id(10L).build();

        UserEntity assignee = UserEntity.builder()
                .id(1L).build();

        CreateTaskRequestDto dto = CreateTaskRequestDto.builder()
                .title("AAAAAAAAAAAAAA")
                .description("AAAAAAAAAAA")
                .assigneeId(1L)
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(1L).build();

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .team(team)
                .build();

        TeamMemberEntity membershipCurrUser = TeamMemberEntity.builder()
                .team(team)
                .user(currentUser)
                .role(TeamRole.OWNER)
                .build();

        TeamMemberEntity membershipAssignee = TeamMemberEntity.builder()
                .team(team)
                .user(assignee)
                .role(TeamRole.MEMBER)
                .build();

        TaskEntity taskEntity = TaskEntity.builder()
                .id(1L)
                .title("AAAAAAAAAAAAAA")
                .description("AAAAAAAAAAA")
                .status(Status.TODO)
                .project(project)
                .assignee(assignee)
                .build();

        TaskEntity savedTask = TaskEntity.builder()
                .id(1L)
                .title("AAAAAAAAAAAAAA")
                .description("AAAAAAAAAAA")
                .status(Status.TODO)
                .project(project)
                .assignee(assignee)
                .build();

        TaskResponseDto dtoResponse = TaskResponseDto.builder()
                .id(1L)
                .title("AAAAAAAAAAAAAA")
                .description("AAAAAAAAAAA")
                .status(Status.TODO)
                .projectId(1L)
                .assigneeId(1L)
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(assignee));

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(teamAccessService.checkMembershipRole(team, currentUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER)))
                .thenReturn(membershipCurrUser);

        when(teamAccessService.checkMembership(team, assignee)).thenReturn(membershipAssignee);
        when(taskMapper.toEntity(dto, project, assignee)).thenReturn(taskEntity);
        when(taskRepository.save(taskEntity)).thenReturn(savedTask);
        when(taskMapper.toDto(savedTask)).thenReturn(dtoResponse);

        TaskResponseDto result = taskService.createTask(1L, dto);
        assertEquals(dtoResponse, result);
        assertEquals(taskEntity.getTitle(), result.getTitle());
        assertEquals(taskEntity.getDescription(), result.getDescription());

        verify(taskRepository).save(taskEntity);
        verify(cacheInvalidationService).evictTaskPagesByProjectId(1L);
        verify(userService).getCurrentUser();
        verify(projectRepository).findById(1L);
        verify(userRepository).findById(1L);
        verify(teamAccessService).checkMembershipRole(team, currentUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER));
        verify(teamAccessService).checkMembership(team, assignee);
        verify(taskMapper).toEntity(dto, project, assignee);
        verify(taskMapper).toDto(savedTask);

    }

    @Test
    void createTask_shouldThrowNotFoundException_whenProjectNotFound(){

        CreateTaskRequestDto dto = CreateTaskRequestDto.builder()
                .title("AAAAAAAAAAAAAA")
                .description("AAAAAAAAAAA")
                .assigneeId(1L)
                .build();

        when(projectRepository.findById(1L)).thenThrow(new NotFoundException("Project Not Found"));

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> taskService.createTask(1L, dto));

        assertEquals("Project Not Found",
                exception.getMessage());

        verify(projectRepository, never()).save(any(ProjectEntity.class));

    }

    @Test
    void createTask_shouldThrowForbiddenException_whenAssigneeNotInTeam(){

        CreateTaskRequestDto dto = CreateTaskRequestDto.builder()
                .title("AAAAAAAAAAAAAA")
                .description("AAAAAAAAAAA")
                .assigneeId(1L)
                .build();

        TeamEntity team = TeamEntity.builder()
                .id(1L).build();

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .team(team)
                .build();

        UserEntity currentUser = UserEntity.builder()
                .id(10L).build();

        UserEntity assignee = UserEntity.builder()
                .id(1L).build();

        TeamMemberEntity membershipCurrUser = TeamMemberEntity.builder()
                .team(team)
                .user(currentUser)
                .role(TeamRole.OWNER)
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(userRepository.findById(1L)).thenReturn(Optional.of(assignee));
        when(userService.getCurrentUser()).thenReturn(currentUser);

        when(teamAccessService.checkMembershipRole(team, currentUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER)))
                .thenReturn(membershipCurrUser);

        when(teamAccessService.checkMembership(team, assignee))
                .thenThrow(new ForbiddenException("Недостаточно прав. User role: MEMBER, Required roles: [OWNER, MANAGER]"));

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> taskService.createTask(1L, dto));

        assertEquals("Недостаточно прав. User role: MEMBER, Required roles: [OWNER, MANAGER]",
                exception.getMessage());

        verify(projectRepository, never()).save(any(ProjectEntity.class));
        verify(projectRepository).findById(1L);
        verify(userRepository).findById(1L);
        verify(userService).getCurrentUser();
        verify(teamAccessService).checkMembershipRole(team, currentUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER));

    }

}
