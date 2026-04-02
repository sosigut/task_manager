package org.example.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.example.config.cache.CacheInvalidationService;
import org.example.dto.CreateProjectRequestDto;
import org.example.dto.ProjectResponseDto;
import org.example.dto.UpdateProjectRequestDto;
import org.example.entity.*;
import org.example.exception.ForbiddenException;
import org.example.exception.NotFoundException;
import org.example.mapper.ProjectMapper;
import org.example.pagination.*;
import org.example.repository.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
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
    private final MeterRegistry meterRegistry;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    private Counter projectsCreatedCounter;
    private Counter projectsDeletedCounter;

    private Timer projectCreatedTimer;
    private Timer projectDeletedTimer;
    private Timer projectUpdatedTimer;
    private Timer projectGetTimer;

    @PostConstruct
    public void initMetrics(){
        this.projectsCreatedCounter = Counter.builder
                        ("task_manager_projects_created_total")
                .description("Total number of projects created")
                .tag("service", "task-manager")
                .register(this.meterRegistry);

        this.projectsDeletedCounter = Counter.builder
                        ("task_manager_projects_delete_total")
                .description("Total number of projects deleted")
                .tag("service", "task-manager")
                .register(this.meterRegistry);

        this.projectCreatedTimer = Timer.builder("task_manager_project_create_timer")
                .description("Time taken to create a project")
                .tag("service", "task-manager")
                .register(this.meterRegistry);

        this.projectDeletedTimer = Timer.builder("task_manager_project_delete_timer")
                .description("Time taken to delete a project")
                .tag("service", "task-manager")
                .register(this.meterRegistry);

        this.projectUpdatedTimer = Timer.builder("task_manager_project_update_timer")
                .description("Time taken to update a project")
                .tag("service", "task-manager")
                .register(this.meterRegistry);

        this.projectGetTimer = Timer.builder("task_manager_project_get_timer")
                .description("Time taken to get projects")
                .tag("service", "task-manager")
                .register(this.meterRegistry);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ProjectResponseDto createProject(CreateProjectRequestDto dto, Long teamId) {

        Timer.Sample sample = Timer.start(meterRegistry);

        if (teamId == null) {
            throw new IllegalArgumentException("Team ID is required");
        }

        if(dto == null){
            throw new IllegalArgumentException("Create project request DTO is required");
        }

        try {
            UserEntity owner = userService.getCurrentUser();

            TeamEntity team = teamRepository.findById(teamId)
                    .orElseThrow(() -> new NotFoundException("Team not found"));

            TeamMemberEntity membership = teamMemberRepository.findByTeamIdAndUserId(teamId, owner.getId())
                    .orElseThrow(() -> new NotFoundException("Team member not found"));

            ProjectEntity project = projectMapper.toEntity(dto, owner, team);
            ProjectEntity saved = projectRepository.save(project);

            Set<TeamRole> allowedRoles = Set.of(TeamRole.OWNER, TeamRole.MANAGER);
            if(!allowedRoles.contains(membership.getRole())){
                throw new ForbiddenException("You don't have permission to create project. Required roles: " + allowedRoles);
            }

            cacheInvalidationService.evictProjectPagesByUserId(owner.getId());
            projectsCreatedCounter.increment();

            return projectMapper.toDto(saved);
        } finally {
            sample.stop(projectCreatedTimer);
        }
    }

    @Cacheable(value = "projectPages",
            keyGenerator = "universalKeyGenerator")
    @PreAuthorize("isAuthenticated()")
    public KeysetPageResponseDto<ProjectResponseDto> getKeysetMyProjects(Integer limit,
                                               LocalDateTime cursorCreatedAt,
                                               Long cursorId) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
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
        } finally {
            sample.stop(projectGetTimer);
        }
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void deleteProject(Long projectId) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
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
            projectsDeletedCounter.increment();
        } finally {
            sample.stop(projectDeletedTimer);
        }
    }

    private static boolean isFlag(UserEntity currentUser) {
        return currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER;
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ProjectResponseDto updateProject(UpdateProjectRequestDto dto, Long projectId) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            ProjectEntity project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new NotFoundException("Project not found"));
            UserEntity currentUser = userService.getCurrentUser();

            if(!isFlag(currentUser)) {
                throw new ForbiddenException("Недостаточно прав");
            }

            if(dto.getDescription() == null && dto.getName() == null) {
                throw new IllegalArgumentException("Нет полей для обновления");
            }

            if(dto.getDescription() != null) {

                String trimmedDescription = dto.getDescription().trim();

                if(trimmedDescription.isEmpty()) {
                    throw new IllegalArgumentException("Описание проекта не должно быть пусты");
                }

                if(trimmedDescription.length() > 5000) {
                    throw new IllegalArgumentException("Описание проекта не должно быть больше 5000 знаков");
                }

                project.setDescription(trimmedDescription);
            }

            if(dto.getName() != null) {

                String trimmedName = dto.getName().trim();

                if(trimmedName.isEmpty()) {
                    throw new IllegalArgumentException("Название проекта не должно быть пусты");
                }

                if(trimmedName.length() > 200) {
                    throw new IllegalArgumentException("Название проекта не должно быть больше 200 знаков");
                }

                project.setName(trimmedName);
            }

            ProjectEntity saveProject = projectRepository.save(project);
            cacheInvalidationService.evictProjectPagesByUserId(project.getOwner().getId());

            return projectMapper.toDto(saveProject);
        } finally {
            sample.stop(projectUpdatedTimer);
        }
    }
}
