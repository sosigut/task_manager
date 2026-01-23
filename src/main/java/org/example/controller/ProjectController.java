package org.example.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.dto.CreateProjectRequestDto;
import org.example.dto.ProjectResponseDto;
import org.example.service.ProjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping("/projects")
    public ProjectResponseDto createProject(@Valid @RequestBody CreateProjectRequestDto dto) {
        return projectService.createProject(dto);
    }

    @GetMapping("/projects/my")
    public List<ProjectResponseDto> getProjects() {
        return projectService.getAllProjects();
    }
}
