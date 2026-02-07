package org.example.service;

import lombok.AllArgsConstructor;
import org.example.dto.CreateProjectRequestDto;
import org.example.dto.KeysetProjectPageResponseDto;
import org.example.dto.ProjectResponseDto;
import org.example.entity.ProjectEntity;
import org.example.entity.Role;
import org.example.entity.TaskEntity;
import org.example.entity.UserEntity;
import org.example.exception.ForbiddenException;
import org.example.mapper.ProjectMapper;
import org.example.repository.ProjectRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final UserService userService;
    private final ProjectRepository projectRepository;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ProjectResponseDto createProject(CreateProjectRequestDto dto) {

        UserEntity owner = userService.getCurrentUser();
        ProjectEntity project = projectMapper.toEntity(dto, owner);
        ProjectEntity saved = projectRepository.save(project);

        return projectMapper.toDto(saved);

    }

    private void validateKeysetCursor(boolean isFirst, boolean isNext) {
        if (isFirst || isNext) return;
        throw new IllegalArgumentException("Неверные параметры курсора");
    }

    @PreAuthorize("isAuthenticated()")
    public KeysetProjectPageResponseDto getMyProjects(Integer limit,
                                                  LocalDateTime cursorCreatedAt,
                                                  Long cursorId) {

        int pageSize = limit != null && limit > 0 ? Math.min(limit, 50) : 10;
        int querySize = pageSize + 1;

        boolean isFirst = cursorCreatedAt == null && cursorId == null;
        boolean isNext  = cursorCreatedAt != null && cursorId != null;

        validateKeysetCursor(isFirst, isNext);

        Pageable pageable = PageRequest.of(
                0,
                querySize,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        UserEntity owner = userService.getCurrentUser();

        Slice<ProjectEntity> slice;

        if (isFirst) {

            slice = projectRepository.findFirstPageByCreatedAtAndOIdDesc(owner.getId(), pageable);

        } else {

            slice = projectRepository.findNextPageByCreatedAtAndIdAfterCursor(owner.getId(),
                    cursorCreatedAt, cursorId, pageable);

        }

        var content = slice.getContent();

        boolean hasNext = content.size() > pageSize;
        var itemsToReturn = hasNext ? content.subList(0, pageSize) : content;

        LocalDateTime nextCursorCreatedAt = null;
        Long nextCursorId = null;

        if(hasNext && !itemsToReturn.isEmpty()) {
            ProjectEntity lastItem = itemsToReturn.get(itemsToReturn.size() - 1);
            nextCursorCreatedAt = lastItem.getCreatedAt();
            nextCursorId = lastItem.getId();
        }

        return KeysetProjectPageResponseDto.builder()
                .items(itemsToReturn.stream().map(projectMapper::toDto).toList())
                .limit(pageSize)
                .cursorCreatedAt(nextCursorCreatedAt)
                .cursorId(nextCursorId)
                .hasNext(hasNext)
                .build();

    }

}
