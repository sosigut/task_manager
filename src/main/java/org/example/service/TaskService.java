package org.example.service;

import lombok.AllArgsConstructor;
import org.example.dto.CreateTaskRequestDto;
import org.example.dto.KeysetTaskPageResponseDto;
import org.example.dto.TaskHistoryResponseDto;
import org.example.dto.TaskResponseDto;
import org.example.entity.*;
import org.example.exception.ForbiddenException;
import org.example.exception.NotFoundException;
import org.example.mapper.TaskHistoryMapper;
import org.example.mapper.TaskMapper;
import org.example.repository.ProjectRepository;
import org.example.repository.TaskHistoryRepository;
import org.example.repository.TaskRepository;
import org.example.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private void validateKeysetCursor(boolean isFirst, boolean isNext) {
        if (isFirst || isNext) return;
        throw new IllegalArgumentException("Неверные параметры курсора");
    }

    @PreAuthorize("isAuthenticated()")
    public KeysetTaskPageResponseDto getKeySetTasksByProject(
            Long projectId,
            Integer limit,
            LocalDateTime cursorCreatedAt,
            Long cursorId
    ) {

        int pageSize = limit != null && limit > 0 ? Math.min(limit, 50) : 10;
        int querySize = pageSize + 1;

        boolean isFirst = cursorCreatedAt == null && cursorId == null;
        boolean isNext  = cursorCreatedAt != null && cursorId != null;

        validateKeysetCursor(isFirst, isNext);

        Pageable pageable = PageRequest.of(
                0,
                querySize,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        UserEntity currentUser = userService.getCurrentUser();

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        Slice<TaskEntity> slice;

        if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER) {

            slice = isFirst
                    ? taskRepository.findFirstPageByProjectId(project.getId(), pageable)
                    : taskRepository.findNextByProjectIdAfterCursor(
                    project.getId(), cursorCreatedAt, cursorId, pageable
            );

        } else {

            slice = isFirst
                    ? taskRepository.findFirstPageByProjectIdAndAssigneeId(
                    project.getId(), currentUser.getId(), pageable
            )
                    : taskRepository.findNextByProjectIdAndAssigneeIdAfterCursor(
                    project.getId(), currentUser.getId(),
                    cursorCreatedAt, cursorId, pageable
            );
        }

        var content = slice.getContent();

        boolean hasNext = content.size() > pageSize;
        var itemsToReturn = hasNext ? content.subList(0, pageSize) : content;

        LocalDateTime nextCursorCreatedAt = null;
        Long nextCursorId = null;

        if (hasNext && !itemsToReturn.isEmpty()) {
            TaskEntity last = itemsToReturn.get(itemsToReturn.size() - 1);
            nextCursorCreatedAt = last.getCreatedAt();
            nextCursorId = last.getId();
        }

        return KeysetTaskPageResponseDto.builder()
                .items(itemsToReturn.stream().map(taskMapper::toDto).toList())
                .limit(pageSize)
                .cursorCreatedAt(nextCursorCreatedAt)
                .cursorId(nextCursorId)
                .hasNext(hasNext)
                .build();
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
