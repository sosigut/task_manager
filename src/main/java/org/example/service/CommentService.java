package org.example.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.example.config.cache.CacheInvalidationService;
import org.example.dto.CommentResponseDto;
import org.example.dto.CreateCommentRequestDto;
import org.example.dto.NotificationDto;
import org.example.entity.*;
import org.example.exception.ForbiddenException;
import org.example.exception.NotFoundException;
import org.example.mapper.CommentMapper;
import org.example.pagination.*;
import org.example.repository.CommentRepository;
import org.example.repository.TaskRepository;
import org.example.repository.TeamMemberRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserService userService;
    private final CommentMapper commentMapper;
    private final KeysetPaginationFetcher keysetPaginationFetcher;
    private final KeysetPaginationUtils keysetPaginationUtils;
    private final KeysetPageBuilder keysetPageBuilder;
    private final CacheInvalidationService cacheInvalidationService;
    private final MeterRegistry meterRegistry;
    private final TeamAccessService teamAccessService;
    private final NotificationService notificationService;

    private Counter commentsCreatedCounter;
    private Counter commentsDeletedCounter;

    private Timer commentCreatedTimer;
    private Timer commentDeletedTimer;
    private Timer commentGetTimer;

    @PostConstruct
    public void initMetrics(){
        this.commentsCreatedCounter = Counter.builder
                        ("task_manager_comments_created_total")
                .description("Total number of comments created")
                .tag("service", "task-manager")
                .register(this.meterRegistry);

        this.commentsDeletedCounter = Counter.builder
                        ("task_manager_comments_deleted_total")
                .description("Total number of comment deleted")
                .tag("service", "task-manager")
                .register(this.meterRegistry);

        this.commentCreatedTimer = Timer.builder("task_manager_comment_create_timer")
                .description("Time taken to create a comment")
                .tag("service", "task-manager")
                .register(this.meterRegistry);

        this.commentDeletedTimer = Timer.builder("task_manager_comment_delete_timer")
                .description("Time taken to delete a comment")
                .tag("service", "task-manager")
                .register(this.meterRegistry);

        this.commentGetTimer = Timer.builder("task_manager_comment_get_timer")
                .description("Time taken to get comments")
                .tag("service", "task-manager")
                .register(this.meterRegistry);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public CommentResponseDto createComment
            (Long taskId, CreateCommentRequestDto dto) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {

            if (taskId == null) {
                throw new IllegalArgumentException("Task ID is required");
            }

            if (dto == null) {
                throw new IllegalArgumentException("Comment DTO is required");
            }

            UserEntity currentUser = userService.getCurrentUser();
            TaskEntity task = taskRepository.findById(taskId)
                    .orElseThrow(()-> new NotFoundException("Task not found"));

            TeamEntity team = task.getProject().getTeam();

            teamAccessService.checkMembership(team, currentUser);

            CommentEntity comment = commentMapper.toEntity(dto, task, currentUser);
            CommentEntity saved =  commentRepository.save(comment);

            cacheInvalidationService.evictCommentPagesByTaskId(taskId);

            commentsCreatedCounter.increment();

            UserEntity assignee = task.getAssignee();

            if(assignee != null && !Objects.equals(currentUser.getId(), assignee.getId())){

                NotificationDto notificationDto = NotificationDto.builder()
                        .type("NEW_COMMENT")
                        .message(String.format("Пользователь %s оставил новый комментарий к вашей задаче", currentUser.getFirstName()))
                        .entityType("TASK")
                        .entityId(taskId)
                        .build();

                notificationService.sendPersonalNotification(assignee.getEmail(),  notificationDto);

            }

            return commentMapper.toDto(saved);
        } finally {
            sample.stop(commentCreatedTimer);
        }
    }

    private boolean checkDeleteCommentPermission(TeamMemberEntity membership,
                                                 UserEntity currentUser,
                                                 CommentEntity comment) {
        TeamRole role = membership.getRole();

        if (role == TeamRole.OWNER || role == TeamRole.MANAGER) {
            return true;
        }

        if (role == TeamRole.MEMBER) {
            return comment.getAuthor().getId().equals(currentUser.getId());
        }

        return false;
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void deleteComment(Long commentId) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            if (commentId == null) {
                throw new IllegalArgumentException("Comment ID is required");
            }

            UserEntity currentUser = userService.getCurrentUser();

            CommentEntity commentEntity = commentRepository.findById(commentId)
                    .orElseThrow(() -> new NotFoundException("Comment not found"));

            TaskEntity task = commentEntity.getTask();
            TeamEntity team = task.getProject().getTeam();
            Long taskId = task.getId();

            TeamMemberEntity membership = teamAccessService.checkMembership(team, currentUser);

            boolean isAllowed = checkDeleteCommentPermission(membership, currentUser, commentEntity);

            if (!isAllowed) {
                throw new ForbiddenException("Недостаточно прав для удаления комментария");
            }

            commentRepository.delete(commentEntity);
            cacheInvalidationService.evictCommentPagesByTaskId(taskId);

            commentsDeletedCounter.increment();
        } finally {
            sample.stop(commentDeletedTimer);
        }
    }

    @Cacheable(value = "commentPages",
    keyGenerator = "universalKeyGenerator")
    @PreAuthorize("isAuthenticated()")
    public KeysetPageResponseDto<CommentResponseDto> getKeysetTaskComments(Long taskId,
                                                              Integer limit,
                                                              LocalDateTime cursorCreatedAt,
                                                              Long cursorId) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {

            if (taskId == null) {
                throw new IllegalArgumentException("Task ID is required");
            }

            PaginationMode mode =  keysetPaginationUtils.cursorMode(cursorCreatedAt, cursorId);
            int pageSize = keysetPaginationUtils.normalizeLimit(limit);
            Pageable pageable = keysetPaginationUtils.createPageable(pageSize);

            UserEntity currentUser = userService.getCurrentUser();

            TaskEntity task = taskRepository.findById(taskId)
                    .orElseThrow(()-> new NotFoundException("Task not found"));

            TeamEntity team = task.getProject().getTeam();

            teamAccessService.checkMembership(team, currentUser);

            Slice<CommentEntity> slice = keysetPaginationFetcher.fetchSlice(
                    mode,
                    () -> commentRepository.findFirstPageByTaskIdOrderByCreatedAtAndIdDesc(
                            task.getId(), pageable
                    ),
                    (createdAt, id) -> commentRepository.findNextPageByTaskIdOrderByCreatedAtAndIdDescAfterCursor(
                            task.getId(), createdAt, id, pageable
                    ),
                    cursorCreatedAt,
                    cursorId
            );

            KeysetSliceResult<CommentEntity> sliceResult = keysetPaginationUtils.trim(
                    slice, pageSize
            );

            return keysetPageBuilder.universalBuilder(
                    sliceResult,
                    commentMapper::toDto,
                    pageSize
            );
        } finally {
            sample.stop(commentGetTimer);
        }
    }
}
