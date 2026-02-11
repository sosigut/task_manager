package org.example.service;

import lombok.AllArgsConstructor;
import org.example.dto.CreateProjectRequestDto;
import org.example.dto.ProjectResponseDto;
import org.example.entity.ProjectEntity;
import org.example.entity.UserEntity;
import org.example.mapper.ProjectMapper;
import org.example.pagination.*;
import org.example.repository.ProjectRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final UserService userService;
    private final ProjectRepository projectRepository;
    private final KeysetPageBuilder keysetPageBuilder;
    private final KeysetPaginationUtils keysetPaginationUtils;
    private final KeysetPaginationFetcher keysetPaginationFetcher;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ProjectResponseDto createProject(CreateProjectRequestDto dto) {

        UserEntity owner = userService.getCurrentUser();
        ProjectEntity project = projectMapper.toEntity(dto, owner);
        ProjectEntity saved = projectRepository.save(project);

        return projectMapper.toDto(saved);

    }

    @PreAuthorize("isAuthenticated()")
    public KeysetPageResponseDto<ProjectResponseDto> getMyProjects(Integer limit,
                                               LocalDateTime cursorCreatedAt,
                                               Long cursorId) {

        PaginationMode mode = keysetPaginationUtils.cursorMode(cursorCreatedAt, cursorId);
        int pageSize = keysetPaginationUtils.normalizeLimit(limit);
        Pageable pageable = keysetPaginationUtils.createPageable(pageSize);

        UserEntity owner = userService.getCurrentUser();

        Slice<ProjectEntity> slice = keysetPaginationFetcher.fetchSlice(
                mode,
                () -> projectRepository.findFirstPageByCreatedAtAndOwnerIdDesc(owner.getId(), pageable),
                (createdAt, id) -> projectRepository.findNextPageByCreatedAtAndOwnerIdAfterCursor(
                        owner.getId(), createdAt, id, pageable
                ),
                cursorCreatedAt,
                cursorId
        );

        KeysetSliceResult<ProjectEntity> sliceResult = keysetPaginationUtils.trim(
                slice, pageSize
        );

        return keysetPageBuilder.universalBuilder(
                sliceResult,
                projectMapper::toDto,
                pageSize
        );

    }

}
