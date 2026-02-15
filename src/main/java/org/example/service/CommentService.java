package org.example.service;

import lombok.AllArgsConstructor;
import org.example.dto.CommentResponseDto;
import org.example.dto.CreateCommentRequestDto;
import org.example.entity.*;
import org.example.exception.ForbiddenException;
import org.example.exception.NotFoundException;
import org.example.mapper.CommentMapper;
import org.example.pagination.*;
import org.example.repository.CommentRepository;
import org.example.repository.TaskRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserService userService;
    private final CommentMapper commentMapper;
    private final KeysetPaginationFetcher keysetPaginationFetcher;
    private final KeysetPaginationUtils keysetPaginationUtils;
    private final KeysetPageBuilder keysetPageBuilder;

    @PreAuthorize("isAuthenticated()")
    public CommentResponseDto createComment
            (Long taskId, CreateCommentRequestDto dto){

        UserEntity currentUser = userService.getCurrentUser();
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(()-> new NotFoundException("Task not found"));

        boolean isAllowed = checkCommentPermission(currentUser, task);

        if(!isAllowed){
            throw new ForbiddenException("Недостаточно прав");
        }

        CommentEntity comment = commentMapper.toEntity(dto, task, currentUser);
        CommentEntity saved =  commentRepository.save(comment);
        return commentMapper.toDto(saved);

    }

    private boolean checkCommentPermission(UserEntity currentUser, TaskEntity task){

        ProjectEntity project = task.getProject();

        if(currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER){
            return true;
        } else if(currentUser.getRole() == Role.USER){
            boolean isAssignee = task.getAssignee().getId().equals(currentUser.getId());
            boolean isOwner = project.getOwner().getId().equals(currentUser.getId());
            if(isAssignee){
                return true;
            }
            return isOwner;
        } else {
            return false;
        }
    }

    @Cacheable(value = "commentsPage")
    @PreAuthorize("isAuthenticated()")
    public KeysetPageResponseDto<CommentResponseDto> getKeysetTaskComments(Long taskId,
                                                              Integer limit,
                                                              LocalDateTime cursorCreatedAt,
                                                              Long cursorId) {

        PaginationMode mode =  keysetPaginationUtils.cursorMode(cursorCreatedAt, cursorId);
        int pageSize = keysetPaginationUtils.normalizeLimit(limit);
        Pageable pageable = keysetPaginationUtils.createPageable(pageSize);

        UserEntity currentUser = userService.getCurrentUser();

        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(()-> new NotFoundException("Task not found"));

        boolean isAllowed = checkCommentPermission(currentUser, task);

        if(!isAllowed){
            throw new ForbiddenException("Недостаточно прав");
        }

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

    }

}
