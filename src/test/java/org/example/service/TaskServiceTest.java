package org.example.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.config.cache.CacheInvalidationService;
import org.example.dto.CreateTaskRequestDto;
import org.example.dto.TaskHistoryResponseDto;
import org.example.dto.TaskResponseDto;
import org.example.dto.UpdateTaskRequestDto;
import org.example.entity.*;
import org.example.exception.ForbiddenException;
import org.example.exception.NotFoundException;
import org.example.mapper.TaskHistoryMapper;
import org.example.mapper.TaskMapper;
import org.example.pagination.*;
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
import java.util.List;
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

    @Spy
    private KeysetPaginationUtils keysetPaginationUtils;

    @Spy
    private KeysetPageBuilder pageBuilder;

    @Mock
    private CommentRepository commentRepository;

    @Spy
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

        org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                .contains("Недостаточно прав")
                .contains("MEMBER")
                .contains("OWNER")
                .contains("MANAGER");

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

        org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                .contains("Недостаточно прав")
                .contains("MEMBER")
                .contains("OWNER")
                .contains("MANAGER");

        verify(projectRepository, never()).save(any(ProjectEntity.class));
        verify(projectRepository).findById(1L);
        verify(userRepository).findById(1L);
        verify(userService).getCurrentUser();
        verify(teamAccessService).checkMembershipRole(team, currentUser, Set.of(TeamRole.OWNER, TeamRole.MANAGER));

    }

    @Test
    void updateTask_shouldUpdateTask_whenUserIsAuthorized(){

        UserEntity currentAssignee = UserEntity.builder()
                .id(1L).build();

        UserEntity updateAssignee = UserEntity.builder()
                .id(10L).build();

        TeamEntity team = TeamEntity.builder()
                .id(1L).build();

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .team(team)
                .build();

        TaskEntity task = TaskEntity.builder()
                .id(1L)
                .title("AAAAAAA")
                .description("AAAAAAA")
                .project(project)
                .assignee(currentAssignee)
                .build();

        UpdateTaskRequestDto dto = UpdateTaskRequestDto.builder()
                .title("BBBBBBBB")
                .description("BBBBBBBB")
                .assigneeId(10L)
                .build();

        TeamMemberEntity membershipCurrAssignee = TeamMemberEntity.builder()
                .id(1L)
                .user(currentAssignee)
                .role(TeamRole.OWNER)
                .team(team)
                .build();

        TeamMemberEntity membershipUpdateAssignee = TeamMemberEntity.builder()
                .id(10L)
                .user(updateAssignee)
                .role(TeamRole.MANAGER)
                .team(team)
                .build();

        TaskEntity savedTask = TaskEntity.builder()
                .id(1L)
                .title("BBBBBBBB")
                .description("BBBBBBBB")
                .project(project)
                .assignee(updateAssignee)
                .build();

        TaskResponseDto taskResponseDto = TaskResponseDto.builder()
                .id(1L)
                .title("BBBBBBBB")
                .description("BBBBBBBB")
                .projectId(1L)
                .assigneeId(10L)
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userService.getCurrentUser()).thenReturn(currentAssignee);

        when(teamAccessService.checkMembershipRole
                (team, currentAssignee, Set.of(TeamRole.OWNER, TeamRole.MANAGER)))
        .thenReturn(membershipCurrAssignee);

        when(userRepository.findById(10L)).thenReturn(Optional.of(updateAssignee));
        when(teamAccessService.checkMembership(team, updateAssignee))
                .thenReturn(membershipUpdateAssignee);

        when(taskRepository.save(task)).thenReturn(savedTask);
        when(taskMapper.toDto(savedTask)).thenReturn(taskResponseDto);

        TaskResponseDto result = taskService.updateTask(dto, 1L);

        assertEquals(taskResponseDto, result);
        assertEquals("BBBBBBBB", result.getTitle());
        assertEquals("BBBBBBBB", result.getDescription());
        assertEquals(10L, result.getAssigneeId());

        verify(taskRepository).save(task);
        verify(taskRepository).findById(1L);
        verify(userService).getCurrentUser();
        verify(teamAccessService).checkMembershipRole(
                team, currentAssignee, Set.of(TeamRole.OWNER, TeamRole.MANAGER)
        );
        verify(userRepository).findById(10L);
        verify(taskMapper).toDto(savedTask);
        verify(teamAccessService).checkMembership(team, updateAssignee);
        verify(cacheInvalidationService).evictTaskPagesByProjectId(project.getId());

    }

    @Test
    void updateTask_shouldThrowIllegalArgumentException_whenTitleIsTooLong(){

        UserEntity currentAssignee = UserEntity.builder()
                .id(1L).build();

        UserEntity updateAssignee = UserEntity.builder()
                .id(10L).build();

        TeamEntity team = TeamEntity.builder()
                .id(1L).build();

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .team(team)
                .build();

        TaskEntity task = TaskEntity.builder()
                .id(1L)
                .title("AAAAAAA")
                .description("AAAAAAA")
                .project(project)
                .assignee(currentAssignee)
                .build();

        UpdateTaskRequestDto dto = UpdateTaskRequestDto.builder()
                .title(" ")
                .description("BBBBBBBB")
                .assigneeId(updateAssignee.getId())
                .build();

        TeamMemberEntity membershipCurrAssignee = TeamMemberEntity.builder()
                .id(1L)
                .user(currentAssignee)
                .role(TeamRole.OWNER)
                .team(team)
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userService.getCurrentUser()).thenReturn(currentAssignee);

        when(teamAccessService.checkMembershipRole
                (team, currentAssignee, Set.of(TeamRole.OWNER, TeamRole.MANAGER)))
                .thenReturn(membershipCurrAssignee);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> taskService.updateTask(dto, task.getId()));

        assertEquals("Название задачи не должно быть пустым", exception.getMessage());

        verify(taskRepository, never()).save(task);
        verify(cacheInvalidationService, never()).evictTaskPagesByProjectId(project.getId());

    }

    @Test
    void updateTask_shouldThrowNotFoundException_whenNewAssigneeDoesNotExist(){

        UserEntity currentAssignee = UserEntity.builder()
                .id(1L).build();

        TeamEntity team = TeamEntity.builder()
                .id(1L).build();

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .team(team)
                .build();

        TaskEntity task = TaskEntity.builder()
                .id(1L)
                .title("AAAAAAA")
                .description("AAAAAAA")
                .project(project)
                .assignee(currentAssignee)
                .build();

        UpdateTaskRequestDto dto = UpdateTaskRequestDto.builder()
                .title("BBBBBBBB")
                .description("BBBBBBBB")
                .assigneeId(10L)
                .build();

        TeamMemberEntity membershipCurrAssignee = TeamMemberEntity.builder()
                .id(1L)
                .user(currentAssignee)
                .role(TeamRole.OWNER)
                .team(team)
                .build();


        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userService.getCurrentUser()).thenReturn(currentAssignee);

        when(teamAccessService.checkMembershipRole
                (team, currentAssignee, Set.of(TeamRole.OWNER, TeamRole.MANAGER)))
                .thenReturn(membershipCurrAssignee);

        when(userRepository.findById(10L)).thenThrow(
                new NotFoundException("Исполнитель задачи не найден")
        );

        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> taskService.updateTask(dto, task.getId()));

        assertEquals("Исполнитель задачи не найден", exception.getMessage());

        verify(taskRepository, never()).save(task);
        verify(cacheInvalidationService, never()).evictTaskPagesByProjectId(project.getId());

    }

    @Test
    void deleteTask_shouldDeleteTaskAndRelatedData_whenUserHasRoles(){

        TeamEntity team = TeamEntity.builder()
                .id(1L).build();

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .team(team)
                .build();

        UserEntity user = UserEntity.builder()
                .id(1L).build();

        TaskEntity task = TaskEntity.builder()
                .id(1L)
                .project(project)
                .assignee(user)
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .id(1L)
                .user(user)
                .role(TeamRole.OWNER)
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userService.getCurrentUser()).thenReturn(user);

        when(teamAccessService.checkMembershipRole
                (team, user, Set.of(TeamRole.OWNER, TeamRole.MANAGER)))
                .thenReturn(membership);

        taskService.deleteTask(1L);

        verify(commentRepository).deleteByTask_Id(task.getId());
        verify(taskHistoryRepository).deleteByTask_Id(task.getId());
        verify(taskRepository).delete(task);
        verify(cacheInvalidationService).evictCommentPagesByTaskId(task.getId());
        verify(cacheInvalidationService).evictTaskPagesByProjectId(project.getId());
        verify(taskRepository).findById(1L);
        verify(userService).getCurrentUser();
        verify(teamAccessService).checkMembershipRole(team, user, Set.of(TeamRole.OWNER, TeamRole.MANAGER));

    }

    @Test
    void deleteTask_shouldThrowForbiddenException_whenUserIsJustMember(){

        TeamEntity team = TeamEntity.builder()
                .id(1L).build();

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .team(team)
                .build();

        UserEntity user = UserEntity.builder()
                .id(1L).build();

        TaskEntity task = TaskEntity.builder()
                .id(1L)
                .project(project)
                .assignee(user)
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(userService.getCurrentUser()).thenReturn(user);

        when(teamAccessService.checkMembershipRole
                (team, user, Set.of(TeamRole.OWNER, TeamRole.MANAGER)))
                .thenThrow(new ForbiddenException("Недостаточно прав. User role: MEMBER, Required roles: [OWNER, MANAGER]"));

        ForbiddenException  exception = assertThrows(ForbiddenException.class,
                () -> taskService.deleteTask(1L));

        org.assertj.core.api.Assertions.assertThat(exception.getMessage())
                .contains("Недостаточно прав")
                .contains("MEMBER")
                .contains("OWNER")
                .contains("MANAGER");

        verify(commentRepository, never()).deleteByTask_Id(task.getId());
        verify(taskHistoryRepository, never()).deleteByTask_Id(task.getId());
        verify(taskRepository, never()).delete(task);
        verify(cacheInvalidationService, never()).evictCommentPagesByTaskId(task.getId());
        verify(cacheInvalidationService, never()).evictTaskPagesByProjectId(project.getId());
        verify(taskRepository).findById(1L);
        verify(userService).getCurrentUser();

    }

    @Test
    void getKeysetTasksByProject_shouldReturnFirstPage_whenNoAssigneeProvided(){

        Integer limit = 10;

        TeamEntity team = TeamEntity.builder()
                .id(1L).build();

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .team(team)
                .build();

        UserEntity user = UserEntity.builder()
                .id(1L).build();

        TaskEntity task = TaskEntity.builder()
                .id(1L)
                .project(project)
                .assignee(user)
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .id(1L)
                .user(user)
                .role(TeamRole.OWNER)
                .build();

        List<TaskEntity> tasks = List.of(task);
        Slice<TaskEntity> taskSlice = new SliceImpl<>(tasks);

        when(userService.getCurrentUser()).thenReturn(user);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(teamAccessService.checkMembership(team, user)).thenReturn(membership);

        when(taskRepository.findFirstPageByProjectId(eq(1L), any(Pageable.class)))
                .thenReturn(taskSlice);

        KeysetPageResponseDto<TaskResponseDto> result = taskService.getKeysetTasksByProject(
                1L, limit, null, null);

        assertNotNull(result);
        assertFalse(result.getItems().isEmpty());
        assertEquals(1, result.getItems().size());

        verify(taskRepository).findFirstPageByProjectId(
                eq(1L), any(Pageable.class));

        verify(taskRepository, never()).findFirstPageByProjectIdAndAssigneeId(
                any(Long.class), any(Long.class), any(Pageable.class));

        verify(pageBuilder).universalBuilder(any(), any(), eq(limit));

    }

    @Test
    void getKeysetTasksByProject_shouldReturnNextPage_whenAssigneeIsProvided(){

        Integer limit = 10;
        Long cursorId = 1L;
        LocalDateTime cursorCreatedAt = LocalDateTime.now();

        TeamEntity team = TeamEntity.builder()
                .id(1L).build();

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .team(team)
                .build();

        UserEntity user = UserEntity.builder()
                .id(1L).build();

        TaskEntity task = TaskEntity.builder()
                .id(1L)
                .project(project)
                .assignee(user)
                .createdAt(LocalDateTime.now())
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .id(1L)
                .user(user)
                .role(TeamRole.MEMBER)
                .build();

        List<TaskEntity> tasks = List.of(task);
        Slice<TaskEntity> taskSlice = new SliceImpl<>(tasks);

        when(userService.getCurrentUser()).thenReturn(user);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(teamAccessService.checkMembership(team, user)).thenReturn(membership);

        when(taskRepository.findNextByProjectIdAndAssigneeIdAfterCursor(
                eq(1L), eq(1L), eq(cursorCreatedAt), eq(cursorId), any(Pageable.class)
        )).thenReturn(taskSlice);

        KeysetPageResponseDto<TaskResponseDto> result = taskService.getKeysetTasksByProject(
                1L, limit, cursorCreatedAt, cursorId);

        assertNotNull(result);
        assertFalse(result.getItems().isEmpty());
        assertEquals(1, result.getItems().size());

        verify(taskRepository).findNextByProjectIdAndAssigneeIdAfterCursor(
                eq(1L), eq(1L), eq(cursorCreatedAt), eq(cursorId), any(Pageable.class));

        verify(taskRepository, never()).findNextByProjectIdAfterCursor(
                any(Long.class), any(LocalDateTime.class), any(Long.class), any(Pageable.class));

        verify(pageBuilder).universalBuilder(any(), any(), eq(limit));

    }

    @Test
    void getTaskHistory_shouldReturnHistoryList_whenUserIsAuthorized(){

        TeamEntity team = TeamEntity.builder()
                .id(1L).build();

        ProjectEntity project = ProjectEntity.builder()
                .id(1L)
                .team(team)
                .build();

        UserEntity currentUser = UserEntity.builder()
                .id(1L).build();

        TaskEntity task = TaskEntity.builder()
                .id(1L)
                .status(Status.TODO)
                .assignee(currentUser)
                .project(project)
                .build();

        TeamMemberEntity membership = TeamMemberEntity.builder()
                .id(1L)
                .team(team)
                .user(currentUser)
                .role(TeamRole.MEMBER)
                .build();

        TaskHistoryEntity history1 = TaskHistoryEntity.builder()
                .id(1L)
                .task(task)
                .oldStatus(Status.TODO)
                .newStatus(Status.IN_PROGRESS)
                .changedBy(currentUser)
                .changedAt(LocalDateTime.now().minusDays(1))
                .build();

        TaskHistoryEntity history2 = TaskHistoryEntity.builder()
                .id(2L)
                .task(task)
                .oldStatus(Status.IN_PROGRESS)
                .newStatus(Status.REVISION)
                .changedBy(currentUser)
                .changedAt(LocalDateTime.now().minusHours(5))
                .build();

        List<TaskHistoryEntity> historyList = List.of(history1, history2);

        TaskHistoryResponseDto responseDto1 = TaskHistoryResponseDto.builder()
                .oldStatus(Status.TODO)
                .newStatus(Status.IN_PROGRESS)
                .changedById(1L)
                .build();

        TaskHistoryResponseDto responseDto2 = TaskHistoryResponseDto.builder()
                .oldStatus(Status.IN_PROGRESS)
                .newStatus(Status.REVISION)
                .changedById(1L)
                .build();

        List<TaskHistoryResponseDto> expectedResponse = List.of(responseDto1, responseDto2);

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(teamAccessService.checkMembership(team, currentUser)).thenReturn(membership);
        when(taskHistoryRepository.findAllByTaskIdOrderByChangedAtAsc(1L)).thenReturn(historyList);
        when(taskHistoryMapper.toDto(history1)).thenReturn(responseDto1);
        when(taskHistoryMapper.toDto(history2)).thenReturn(responseDto2);

        List<TaskHistoryResponseDto> result = taskService.getTaskHistory(1L);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());
        assertEquals(expectedResponse, result);

        verify(userService).getCurrentUser();
        verify(taskRepository).findById(1L);
        verify(teamAccessService).checkMembership(team, currentUser);
        verify(taskHistoryRepository).findAllByTaskIdOrderByChangedAtAsc(1L);
        verify(taskHistoryMapper, times(2)).toDto(any(TaskHistoryEntity.class));

    }

}
