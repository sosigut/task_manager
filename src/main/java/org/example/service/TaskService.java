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
import org.example.mapper.UserMapper;
import org.example.repository.ProjectRepository;
import org.example.repository.TaskHistoryRepository;
import org.example.repository.TaskRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
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

    public TaskResponseDto createTask(Long projectId, CreateTaskRequestDto dto){

        //Будет нужен для проверки прав
        UserEntity currentUser = userService.getCurrentUser();

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        UserEntity assignee = userRepository.findById(dto.getAssigneeId())
                .orElseThrow(() -> new NotFoundException("Assignee not found"));

        if(currentUser.getRole() == Role.USER) {
            throw new ForbiddenException(String.format("Пользователь с ролью %s не может создавать задачу", Role.USER));
        }

        TaskEntity task = taskMapper.toEntity(dto, project, assignee);

        TaskEntity saved = taskRepository.save(task);

        return taskMapper.toDto(saved);

    }

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

    public List<TaskResponseDto> getTasksByProject(Long projectId) {

        UserEntity currentUser = userService.getCurrentUser();

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        List<TaskEntity> tasks;
        if(currentUser.getRole() == Role.MANAGER || currentUser.getRole() == Role.ADMIN){

            tasks = taskRepository.findAllByProjectId(project.getId());
        }
        else{

            tasks = taskRepository.findAllByProjectIdAndAssigneeId(project.getId(), currentUser.getId());

        }
        return tasks.stream().map(taskMapper::toDto).collect(Collectors.toList());
    }

    public List<TaskHistoryResponseDto> getTaskHistory(Long taskId) {

        TaskEntity task =  taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));

        List<TaskHistoryEntity> history = taskHistoryRepository
                .findAllByTaskIdOrderByChangedAtAsc(task.getId());

        return history.stream().map(taskHistoryMapper::toDto).collect(Collectors.toList());

    }
}
