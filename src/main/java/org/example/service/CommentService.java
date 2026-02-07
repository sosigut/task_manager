package org.example.service;

import lombok.AllArgsConstructor;
import org.example.dto.CommentResponseDto;
import org.example.dto.CreateCommentRequestDto;
import org.example.dto.KeysetCommentPageResponseDto;
import org.example.entity.*;
import org.example.exception.ForbiddenException;
import org.example.exception.NotFoundException;
import org.example.mapper.CommentMapper;
import org.example.repository.CommentRepository;
import org.example.repository.TaskRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
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

    private void validateKeysetCursor(boolean isFirst, boolean isNext) {
        if (isFirst || isNext) return;
        throw new IllegalArgumentException("Неверные параметры курсора");
    }

    @PreAuthorize("isAuthenticated()")
    public KeysetCommentPageResponseDto getKeySetTaskComments(Long taskId,
                                                              Integer limit,
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

        UserEntity currentUser = userService.getCurrentUser();

        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(()-> new NotFoundException("Task not found"));

        boolean isAllowed = checkCommentPermission(currentUser, task);

        Slice<CommentEntity> slice;

        if(!isAllowed){

            throw new ForbiddenException("Недостаточно прав доступа");

        } else {

            slice = isFirst
                    ? commentRepository.findFirstPageByTaskIdOrderByCreatedAtAndIdDesc(task.getId(), pageable)
                    : commentRepository.findNextPageByTaskIdOrderByCreatedAtAndIdDescAfterCursor(
                    task.getId(), cursorCreatedAt, cursorId, pageable
            );

        }

        var content = slice.getContent();

        boolean hasNext = content.size() > pageSize;
        var itemsToReturn = hasNext ? content.subList(0, pageSize) : content;

        LocalDateTime nextCursorCreatedAt = null;
        Long nextCursorId = null;

        if(hasNext && !itemsToReturn.isEmpty()){
            CommentEntity lastItem = itemsToReturn.get(itemsToReturn.size() - 1);
            nextCursorCreatedAt = lastItem.getCreatedAt();
            nextCursorId = lastItem.getId();
        }

        return KeysetCommentPageResponseDto.builder()
                .items(itemsToReturn.stream().map(commentMapper::toDto).toList())
                .limit(pageSize)
                .cursorCreatedAt(nextCursorCreatedAt)
                .cursorId(nextCursorId)
                .hasNext(hasNext)
                .build();

    }

}
