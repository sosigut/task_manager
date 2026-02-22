package org.example.service;

import lombok.AllArgsConstructor;
import org.example.config.cache.CacheInvalidationService;
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

import javax.swing.text.html.Option;
import java.time.LocalDateTime;
import java.util.Optional;

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
    private final CacheInvalidationService cacheInvalidationService;

    @PreAuthorize("isAuthenticated()")
    public CommentResponseDto createComment
            (Long taskId, CreateCommentRequestDto dto) {

        UserEntity currentUser = userService.getCurrentUser();
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(()-> new NotFoundException("Task not found"));

        boolean isAllowed = checkCommentPermission(currentUser, task);

        if(!isAllowed){
            throw new ForbiddenException("Недостаточно прав");
        }

        CommentEntity comment = commentMapper.toEntity(dto, task, currentUser);
        CommentEntity saved =  commentRepository.save(comment);

        cacheInvalidationService.evictCommentPagesByTaskId(taskId);

        return commentMapper.toDto(saved);

    }

    public String deleteComment(Long commentId) {

        Optional<CommentEntity> comment = commentRepository.findById(commentId);
        UserEntity currentUser = userService.getCurrentUser();

        if(comment.isEmpty()){
            throw new NotFoundException("Comment not found");
        }

        CommentEntity commentEntity = comment.get();
        Long taskId = commentEntity.getTask().getId();
        boolean flag = isFlag(currentUser, commentEntity);

        if(!flag){
            throw new ForbiddenException("Недостаточно прав");
        }

        commentRepository.delete(commentEntity);
        cacheInvalidationService.evictCommentPagesByTaskId(taskId);
        return "Удаление выполнено успешно";
    }

    private static boolean isFlag(UserEntity currentUser, CommentEntity commentEntity) {
        boolean flag;

        if(currentUser.getRole() == Role.MANAGER || currentUser.getRole() == Role.ADMIN){
            flag = true;
        } else if(currentUser.getRole() == Role.USER){
            flag = commentEntity.getAuthor().getId().equals(currentUser.getId());
        } else {
            flag = false;
        }
        return flag;
    }

    private boolean checkCommentPermission(UserEntity currentUser, TaskEntity task){

        if(currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER){
            return true;
        } else if(currentUser.getRole() == Role.USER){
            return task.getAssignee().getId().equals(currentUser.getId());
        } else {
            return false;
        }

    }

    @Cacheable(value = "commentPages",
    keyGenerator = "universalKeyGenerator")
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
