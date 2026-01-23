package org.example.mapper;

import org.example.dto.CreateProjectRequestDto;
import org.example.dto.ProjectResponseDto;
import org.example.entity.ProjectEntity;
import org.example.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ProjectMapper {

    public ProjectEntity toEntity
            (CreateProjectRequestDto dto,
             UserEntity owner) {
        if (dto == null) {
            return null;
        }

        return ProjectEntity.builder()
                .name(dto.getName().trim())
                .description(dto.getDescription().trim())
                .owner(owner)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public ProjectResponseDto toDto(ProjectEntity entity) {
        if (entity == null) {
            return null;
        }

        return ProjectResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .ownerId(entity.getOwner().getId())
                .createdAt(entity.getCreatedAt())
                .build();
    }

}
