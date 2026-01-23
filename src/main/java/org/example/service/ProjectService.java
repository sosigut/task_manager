package org.example.service;

import lombok.AllArgsConstructor;
import org.example.dto.CreateProjectRequestDto;
import org.example.dto.ProjectResponseDto;
import org.example.entity.ProjectEntity;
import org.example.entity.UserEntity;
import org.example.mapper.ProjectMapper;
import org.example.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final UserService userService;
    private final ProjectRepository projectRepository;

    public ProjectResponseDto createProject(CreateProjectRequestDto dto) {

        UserEntity owner = userService.getCurrentUser();
        ProjectEntity project = projectMapper.toEntity(dto, owner);
        ProjectEntity saved = projectRepository.save(project);

        return projectMapper.toDto(saved);

    }

    public List<ProjectResponseDto> getAllProjects() {

        UserEntity owner = userService.getCurrentUser();
        List<ProjectEntity> projects = projectRepository.findAllByOwnerId(owner.getId());

        return projects.stream().map(projectMapper::toDto).collect(Collectors.toList());

    }

}
