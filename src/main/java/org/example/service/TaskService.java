package org.example.service;

import lombok.AllArgsConstructor;
import org.example.dto.CreateTaskRequestDto;
import org.example.dto.TaskHistoryResponseDto;
import org.example.dto.TaskResponseDto;
import org.example.entity.*;
import org.example.exception.ForbiddenException;
import org.example.exception.NotFoundException;
import org.example.mapper.TaskHistoryMapper;
import org.example.mapper.TaskMapper;
import org.example.pagination.*;
import org.example.repository.ProjectRepository;
import org.example.repository.TaskHistoryRepository;
import org.example.repository.TaskRepository;
import org.example.repository.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TaskMapper taskMapper;
    private final TaskHistoryMapper taskHistoryMapper;
    private final TaskHistoryRepository taskHistoryRepository;
    private final KeysetPaginationUtils keysetPaginationUtils;
    private final KeysetPageBuilder pageBuilder;
    private final KeysetPaginationFetcher keysetPaginationFetcher;

    private static final Map<Status, Set<Status>> ALLOWED_TRANSITIONS =Map.of(
            Status.TODO, Set.of(Status.IN_PROGRESS),
            Status.IN_PROGRESS, Set.of(Status.REVISION),
            Status.REVISION, Set.of(Status.IN_PROGRESS)
    );

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public TaskResponseDto createTask(Long projectId, CreateTaskRequestDto dto){

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        UserEntity assignee = userRepository.findById(dto.getAssigneeId())
                .orElseThrow(() -> new NotFoundException("Assignee not found"));

        TaskEntity task = taskMapper.toEntity(dto, project, assignee);

        TaskEntity saved = taskRepository.save(task);

        return taskMapper.toDto(saved);

    }

    @PreAuthorize("isAuthenticated()")
    public TaskResponseDto changeStatus(Long id, Status newStatus){

        UserEntity currentUser = userService.getCurrentUser();

        TaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Task not found"));

        Status oldStatus = task.getStatus();

        if(currentUser.getRole() == Role.USER &&
                !currentUser.getId().equals(task.getAssignee().getId())) {
            throw new ForbiddenException("Только исполнитель задачи может изменить ее статус");
        }

        if(currentUser.getRole() == Role.USER && newStatus == Status.DONE){
            throw new ForbiddenException("Недостаточно прав доступа");
        }

        if(currentUser.getRole() == Role.USER){
            Set<Status> allowedNewStatuses = ALLOWED_TRANSITIONS.get(oldStatus);

            if(allowedNewStatuses == null || !allowedNewStatuses.contains(newStatus)){
                throw new ForbiddenException(String.format("Недопустимый переход статуса: %s -> %s.", oldStatus, newStatus));
            }
        }

        task.setStatus(newStatus);

        taskRepository.save(task);

        TaskHistoryEntity taskHistory = TaskHistoryEntity.builder()
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .task(task)
                .changedBy(currentUser)
                .changedAt(LocalDateTime.now())
                .build();

        taskHistoryRepository.save(taskHistory);

        return taskMapper.toDto(task);

    }

    @PreAuthorize("isAuthenticated()")
    public KeysetPageResponseDto<TaskResponseDto> getKeysetTasksByProject(
            Long projectId,
            Integer limit,
            LocalDateTime cursorCreatedAt,
            Long cursorId
    ) throws NotFoundException {

        PaginationMode mode = keysetPaginationUtils.cursorMode(cursorCreatedAt, cursorId);
        int pageSize = keysetPaginationUtils.normalizeLimit(limit);
        Pageable pageable = keysetPaginationUtils.createPageable(pageSize);

        Supplier<Slice<TaskEntity>> firstPageSupplier;
        BiFunction<LocalDateTime, Long, Slice<TaskEntity>> nextPageSupplier;

        UserEntity currentUser = userService.getCurrentUser();

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        boolean isAdminOrManager = currentUser.getRole() == Role.ADMIN ||
                currentUser.getRole() == Role.MANAGER;

        if (isAdminOrManager) {
            firstPageSupplier = () -> taskRepository.findFirstPageByProjectId(project.getId(), pageable);
            nextPageSupplier = (createdAt, id) ->
                    taskRepository.findNextByProjectIdAfterCursor(project.getId(), createdAt, id, pageable);
        } else {
            firstPageSupplier = () -> taskRepository.findFirstPageByProjectIdAndAssigneeId(
                    project.getId(), currentUser.getId(), pageable);
            nextPageSupplier = (createdAt, id) ->
                    taskRepository.findNextByProjectIdAndAssigneeIdAfterCursor(
                            project.getId(), currentUser.getId(), createdAt, id, pageable);
        }

        Slice<TaskEntity> slice = keysetPaginationFetcher.fetchSlice(
                mode,
                firstPageSupplier,
                nextPageSupplier,
                cursorCreatedAt,
                cursorId
        );

        KeysetSliceResult<TaskEntity> sliceResult =
                keysetPaginationUtils.trim(
                        slice,
                        pageSize
                );

        return pageBuilder.universalBuilder(
                sliceResult,
                taskMapper::toDto,
                pageSize
        );
    }

    @PreAuthorize("isAuthenticated()")
    public List<TaskHistoryResponseDto> getTaskHistory(Long taskId) {

        UserEntity currentUser = userService.getCurrentUser();

        TaskEntity task =  taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));

        if(currentUser.getRole() == Role.USER
                && !currentUser.getId().equals(task.getAssignee().getId())) {
            throw new ForbiddenException("Недостаточно прав доступа");
        }

        List<TaskHistoryEntity> history = taskHistoryRepository
                .findAllByTaskIdOrderByChangedAtAsc(task.getId());

        return history.stream().map(taskHistoryMapper::toDto).collect(Collectors.toList());

    }
}
