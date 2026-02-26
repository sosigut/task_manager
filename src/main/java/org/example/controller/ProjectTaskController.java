package org.example.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.dto.CreateTaskRequestDto;
import org.example.dto.TaskResponseDto;
import org.example.dto.UpdateTaskRequestDto;
import org.example.pagination.KeysetPageResponseDto;
import org.example.service.TaskService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@AllArgsConstructor
@RequestMapping("/projects")
public class ProjectTaskController {

    private final TaskService taskService;

    @PostMapping("/{projectId}/tasks")
    public TaskResponseDto createTask
            (@PathVariable Long projectId,
             @Valid @RequestBody CreateTaskRequestDto dto) {
        return taskService.createTask(projectId, dto);
    }


    @GetMapping("/{projectId}/tasks")
    public KeysetPageResponseDto<TaskResponseDto> getProjectTasks(
            @PathVariable Long projectId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @RequestParam(required = false) Long cursorId){
        return taskService.getKeysetTasksByProject(projectId, limit, cursorCreatedAt, cursorId);
    }

    @DeleteMapping("/task/{taskId}")
    public void deleteTask(@PathVariable Long taskId) {
        taskService.deleteTask(taskId);
    }

    @PatchMapping("/task/{taskId}")
    public TaskResponseDto updateTask(@PathVariable Long taskId,
                                      @Valid @RequestBody UpdateTaskRequestDto dto) {
        return taskService.updateTask(dto, taskId);
    }

}
