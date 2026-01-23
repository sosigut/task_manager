package org.example.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.example.dto.CreateTaskRequestDto;
import org.example.dto.TaskResponseDto;
import org.example.entity.Status;
import org.example.service.TaskService;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/projects")
public class TaskController {

    private TaskService taskService;

    @PostMapping("/{projectId}/tasks")
    public TaskResponseDto createTask (@PathVariable Long projectId, @Valid @RequestBody CreateTaskRequestDto dto){
        return taskService.createTask(projectId, dto);
    }

    @PatchMapping("/tasks/{taskId}/status")
    public TaskResponseDto updateTaskStatus(@PathVariable Long taskId, @Valid @RequestBody Status status){
        return taskService.changeStatus(taskId, status);
    }


}
