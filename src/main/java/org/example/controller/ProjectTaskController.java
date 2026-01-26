package org.example.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.dto.CreateTaskRequestDto;
import org.example.dto.TaskResponseDto;
import org.example.dto.UpdateTaskStatusRequestDto;
import org.example.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/projects")
public class ProjectTaskController {

    private final TaskService taskService;

    @PostMapping("/{projectId}/tasks")
    public TaskResponseDto createTask
            (@PathVariable Long projectId,
             @Valid @RequestBody CreateTaskRequestDto dto){
        return taskService.createTask(projectId, dto);
    }


    @GetMapping("/{projectId}/tasks")
    public List<TaskResponseDto> getProjectTasks(@PathVariable Long projectId){
        return taskService.getTasksByProject(projectId);
    }


}
