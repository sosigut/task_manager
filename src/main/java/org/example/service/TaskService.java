package org.example.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
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
    private final CommentRepository commentRepository;
    private final KeysetPaginationFetcher keysetPaginationFetcher;
    private final CacheInvalidationService cacheInvalidationService;
    private final MeterRegistry meterRegistry;

    private Counter tasksCreatedCounter;
    private Counter tasksDeletedCounter;

    private final Map<String, Counter> statusChangeCounters = new ConcurrentHashMap<>();

    private static final Map<Status, Set<Status>> ALLOWED_TRANSITIONS =Map.of(
            Status.TODO, Set.of(Status.IN_PROGRESS),
            Status.IN_PROGRESS, Set.of(Status.REVISION),
            Status.REVISION, Set.of(Status.IN_PROGRESS)
    );

    @PostConstruct
    public void initMetrics() {
        this.tasksCreatedCounter = Counter.builder
                        ("task_manager_tasks_created_total")
                .description("Total number of tasks created")
                .tag("service", "task-manager")
                .register(meterRegistry);

        this.tasksDeletedCounter = Counter.builder
                        ("task_manager_tasks_deleted_total")
                .description("Total number of tasks deleted")
                .tag("service", "task-manager")
                .register(meterRegistry);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public TaskResponseDto createTask(Long projectId, CreateTaskRequestDto dto) {

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        UserEntity assignee = userRepository.findById(dto.getAssigneeId())
                .orElseThrow(() -> new NotFoundException("Assignee not found"));

        TaskEntity task = taskMapper.toEntity(dto, project, assignee);

        TaskEntity saved = taskRepository.save(task);

        cacheInvalidationService.evictTaskPagesByProjectId(projectId);
        tasksCreatedCounter.increment();

        return taskMapper.toDto(saved);

    }

    private Counter getStatusChangeCounter(Status from, Status to) {
        String key = from.name() + "->" + to.name();

        return statusChangeCounters.computeIfAbsent(key, k ->
                Counter.builder("tasks_status_changed_total")
                        .tag("from", from.name())
                        .tag("to", to.name())
                        .tag("service", "task-manager")
                        .register(meterRegistry)
        );
    }

    @PreAuthorize("isAuthenticated()")
    public TaskResponseDto changeStatus(Long id, Status newStatus) {

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

        cacheInvalidationService.evictTaskPagesByProjectId(task.getProject().getId());

        TaskHistoryEntity taskHistory = TaskHistoryEntity.builder()
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .task(task)
                .changedBy(currentUser)
                .changedAt(LocalDateTime.now())
                .build();

        taskHistoryRepository.save(taskHistory);

        getStatusChangeCounter(oldStatus, newStatus).increment();

        return taskMapper.toDto(task);

    }

    @Cacheable(value = "taskPages",
            keyGenerator = "universalKeyGenerator")
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

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void deleteTask(Long taskId) {

        TaskEntity task = taskRepository.findById(taskId).orElseThrow(() -> new NotFoundException("Task not found"));

        UserEntity currentUser = userService.getCurrentUser();

        boolean flag = isFlag(currentUser);

        if(!flag){
            throw new ForbiddenException("Недостаточно прав");
        }

        commentRepository.deleteByTask_Id(taskId);
        taskHistoryRepository.deleteByTask_Id(taskId);
        taskRepository.delete(task);
        cacheInvalidationService.evictCommentPagesByTaskId(task.getId());
        cacheInvalidationService.evictTaskPagesByProjectId(task.getProject().getId());
        tasksDeletedCounter.increment();

    }

    private static boolean isFlag(UserEntity currentUser){
        return currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER;
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

    @Transactional
    public TaskResponseDto updateTask(UpdateTaskRequestDto dto,  Long taskId) {

        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        UserEntity currentUser = userService.getCurrentUser();

        if(!isFlag(currentUser)){
            throw new ForbiddenException("Недостаточно прав");
        }

        if(dto.getAssigneeId() == null && dto.getDescription() == null && dto.getTitle() == null){
            throw new IllegalArgumentException("Нет полей для обновления");
        }

        if(dto.getTitle() != null){

            String trimmedTitle = dto.getTitle().trim();

            if(trimmedTitle.isEmpty()){
                throw new IllegalArgumentException("Название задачи не должно быть пустым");
            }

            if(trimmedTitle.length() > 200){
                throw new IllegalArgumentException("Название задачи не должно быть больше 200 знаков");
            }

            task.setTitle(trimmedTitle);
        }

        if(dto.getDescription() != null){

            String trimmedDescription = dto.getDescription().trim();

            if(trimmedDescription.isEmpty()){
                throw new IllegalArgumentException("Описание задачи не должно быть пустым");
            }

            if(trimmedDescription.length() > 5000){
                throw new IllegalArgumentException("Описание задачи не должно быть больше 5000 знаков");
            }

            task.setDescription(trimmedDescription);
        }

        if(dto.getAssigneeId() != null){

            UserEntity assignee = userRepository.findById(dto.getAssigneeId())
                    .orElseThrow(() -> new NotFoundException("Исполнитель задачи не найден"));

            task.setAssignee(assignee);
        }

        TaskEntity savedTask = taskRepository.save(task);
        cacheInvalidationService.evictTaskPagesByProjectId(task.getProject().getId());

        return taskMapper.toDto(savedTask);
    }
}
