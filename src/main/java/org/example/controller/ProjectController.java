package org.example.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.dto.CreateProjectRequestDto;
import org.example.dto.ProjectResponseDto;
import org.example.dto.UpdateProjectRequestDto;
import org.example.pagination.KeysetPageResponseDto;
import org.example.service.ProjectService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@AllArgsConstructor
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ProjectResponseDto createProject(@Valid @RequestBody CreateProjectRequestDto dto) {
        return projectService.createProject(dto);
    }

    @GetMapping("/my")
    public KeysetPageResponseDto<ProjectResponseDto> getProjects(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @RequestParam(required = false) Long cursorId) {
        return projectService.getKeysetMyProjects(limit, cursorCreatedAt, cursorId);
    }

    @DeleteMapping("/{projectId}")
    public void deleteProject(@PathVariable Long projectId) {
        projectService.deleteProject(projectId);
    }

    @PatchMapping("/{projectId}")
    public ProjectResponseDto updateProject(@PathVariable Long projectId,
                                            @Valid @RequestBody UpdateProjectRequestDto dto) {
        return projectService.updateProject(dto, projectId);
    }
}
