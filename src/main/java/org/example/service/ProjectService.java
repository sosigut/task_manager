package org.example.service;

import lombok.AllArgsConstructor;
import org.example.config.cache.CacheInvalidationService;
import org.example.dto.CreateProjectRequestDto;
import org.example.dto.ProjectResponseDto;
import org.example.entity.ProjectEntity;
import org.example.entity.Role;
import org.example.entity.TaskEntity;
import org.example.entity.UserEntity;
import org.example.exception.ForbiddenException;
import org.example.exception.NotFoundException;
import org.example.mapper.ProjectMapper;
import org.example.pagination.*;
import org.example.repository.CommentRepository;
import org.example.repository.ProjectRepository;
import org.example.repository.TaskHistoryRepository;
import org.example.repository.TaskRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final UserService userService;
    private final ProjectRepository projectRepository;
    private final KeysetPageBuilder keysetPageBuilder;
    private final KeysetPaginationUtils keysetPaginationUtils;
    private final KeysetPaginationFetcher keysetPaginationFetcher;
    private final CacheInvalidationService cacheInvalidationService;
    private final TaskRepository taskRepository;
    private final CommentRepository commentRepository;
    private final TaskHistoryRepository taskHistoryRepository;

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ProjectResponseDto createProject(CreateProjectRequestDto dto) {

        UserEntity owner = userService.getCurrentUser();
        ProjectEntity project = projectMapper.toEntity(dto, owner);
        ProjectEntity saved = projectRepository.save(project);

        cacheInvalidationService.evictProjectPagesByUserId(owner.getId());

        return projectMapper.toDto(saved);

    }

    @Cacheable(value = "projectPages",
            keyGenerator = "universalKeyGenerator")
    @PreAuthorize("isAuthenticated()")
    public KeysetPageResponseDto<ProjectResponseDto> getKeysetMyProjects(Integer limit,
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

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void deleteProject(Long projectId) {

        ProjectEntity projectEntity = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        UserEntity currentUser = userService.getCurrentUser();
        if (!isFlag(currentUser)) {
            throw new ForbiddenException("Недостаточно прав");
        }

        List<Long> taskIds = taskRepository.findTaskIdsByProject_Id(projectEntity.getId());

        taskHistoryRepository.deleteByTask_ProjectId(projectEntity.getId());
        commentRepository.deleteByTask_ProjectId(projectEntity.getId());

        for (Long taskId : taskIds) {
            cacheInvalidationService.evictCommentPagesByTaskId(taskId);
        }

        taskRepository.deleteByProject_Id(projectEntity.getId());
        cacheInvalidationService.evictTaskPagesByProjectId(projectEntity.getId());
        projectRepository.delete(projectEntity);

        cacheInvalidationService.evictProjectPagesByUserId(projectEntity.getOwner().getId());

    }

    private static boolean isFlag(UserEntity currentUser) {
        return currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER;
    }

}
