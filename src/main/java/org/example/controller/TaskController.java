package org.example.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.dto.TaskHistoryResponseDto;
import org.example.dto.TaskResponseDto;
import org.example.dto.UpdateTaskStatusRequestDto;
import org.example.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    @PatchMapping("/{taskId}/status")
    public TaskResponseDto updateTaskStatus(@PathVariable Long taskId, @Valid @RequestBody UpdateTaskStatusRequestDto dto){
        return taskService.changeStatus(taskId, dto.getNewStatus());
    }

    @GetMapping("/{taskId}/history")
    public List<TaskHistoryResponseDto> getTaskHistory(@PathVariable Long taskId){
        return taskService.getTaskHistory(taskId);
    }

}
