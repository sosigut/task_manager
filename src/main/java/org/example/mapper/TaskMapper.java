package org.example.mapper;

import org.example.dto.CreateTaskRequestDto;
import org.example.dto.TaskResponseDto;
import org.example.entity.ProjectEntity;
import org.example.entity.Status;
import org.example.entity.TaskEntity;
import org.example.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class TaskMapper {

    public TaskEntity toEntity
            (CreateTaskRequestDto dto,
             ProjectEntity project,
             UserEntity assignee) {

        if(dto == null) return null;

        return TaskEntity.builder()
                .title(dto.getTitle().trim())
                .description(dto.getDescription().trim())
                .status(Status.TODO)
                .project(project)
                .assignee(assignee)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public TaskResponseDto toDto (TaskEntity entity) {

        if(entity == null) return null;

        Long projectId = entity.getProject().getId();
        Long assigneeId = entity.getAssignee().getId();

        return TaskResponseDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .projectId(projectId)
                .assigneeId(assigneeId)
                .createdAt(entity.getCreatedAt())
                .build();

    }

}
