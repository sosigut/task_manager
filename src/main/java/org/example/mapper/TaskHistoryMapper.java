package org.example.mapper;

import org.example.dto.TaskHistoryResponseDto;
import org.example.entity.TaskHistoryEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Component;

@Component
public class TaskHistoryMapper {

    @EntityGraph
    public TaskHistoryResponseDto toDto (TaskHistoryEntity entity) {
        if (entity == null) {
            return null;
        }

        Long changedById = entity.getChangedBy().getId();

        return TaskHistoryResponseDto.builder()
                .oldStatus(entity.getOldStatus())
                .newStatus(entity.getNewStatus())
                .changedAt(entity.getChangedAt())
                .changedById(changedById)
                .build();

    }

}
