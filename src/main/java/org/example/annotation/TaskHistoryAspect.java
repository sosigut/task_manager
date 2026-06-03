package org.example.annotation;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.entity.Status;
import org.example.entity.TaskEntity;
import org.example.entity.TaskHistoryEntity;
import org.example.entity.UserEntity;
import org.example.exception.NotFoundException;
import org.example.repository.TaskHistoryRepository;
import org.example.repository.TaskRepository;
import org.example.service.TaskService;
import org.example.service.UserService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
public class TaskHistoryAspect {

    private final UserService userService;
    private final TaskRepository taskRepository;
    private final TaskHistoryRepository taskHistoryRepository;

    @Around("@annotation(org.example.annotation.TrackTaskHistory)")
    public Object TaskChangeStatus(ProceedingJoinPoint joinPoint) throws Throwable{

        Object[] args = joinPoint.getArgs();
        Long taskId = (Long) args[0];
        Status newStatus = (Status) args[1];
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        Status oldStatus = task.getStatus();

        Object result = joinPoint.proceed();

        UserEntity currentUser = userService.getCurrentUser();

        TaskHistoryEntity taskHistory = TaskHistoryEntity.builder()
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .task(task)
                .changedBy(currentUser)
                .changedAt(LocalDateTime.now())
                .build();

        taskHistoryRepository.save(taskHistory);

        return result;
    }

}
