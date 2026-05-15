package org.example.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.config.cache.CacheInvalidationService;
import org.example.dto.TaskResponseDto;
import org.example.entity.*;
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

}
