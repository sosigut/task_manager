package org.example.service;

import lombok.AllArgsConstructor;
import org.example.dto.CreateTaskRequestDto;
import org.example.dto.TaskResponseDto;
import org.example.entity.*;
import org.example.exception.NotFoundException;
import org.example.mapper.TaskMapper;
import org.example.mapper.UserMapper;
import org.example.repository.ProjectRepository;
import org.example.repository.TaskHistoryRepository;
import org.example.repository.TaskRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TaskMapper taskMapper;
    private final TaskHistoryRepository taskHistoryRepository;

    public TaskResponseDto createTask(Long projectId, CreateTaskRequestDto dto){

        //Будет нужен для проверки прав
        UserEntity currentUser = userService.getCurrentUser();

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        UserEntity assignee = userRepository.findById(dto.getAssigneeId())
                .orElseThrow(() -> new NotFoundException("Assignee not found"));

        TaskEntity task = taskMapper.toEntity(dto, project, assignee);

        taskRepository.save(task);

        return taskMapper.toDto(task);

    }

    public TaskResponseDto changeStatus(Long id, Status newStatus){

        UserEntity currentUser = userService.getCurrentUser();

        TaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Task not found"));

        Status oldStatus = task.getStatus();
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



}
