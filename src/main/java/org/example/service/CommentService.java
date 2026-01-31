package org.example.service;

import lombok.AllArgsConstructor;
import org.example.dto.CommentResponseDto;
import org.example.dto.CreateCommentRequestDto;
import org.example.entity.*;
import org.example.exception.ForbiddenException;
import org.example.exception.NotFoundException;
import org.example.mapper.CommentMapper;
import org.example.repository.CommentRepository;
import org.example.repository.TaskRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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

        checkCommentPermission(currentUser, task);

        CommentEntity comment = commentMapper.toEntity(dto, task, currentUser);
        CommentEntity saved =  commentRepository.save(comment);
        return commentMapper.toDto(saved);

    }

    private void checkCommentPermission(UserEntity currentUser, TaskEntity task){

        ProjectEntity project = task.getProject();

        if(currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER){
            return;
        } else if(currentUser.getRole() == Role.USER){
            boolean isAssignee = task.getAssignee().getId().equals(currentUser.getId());
            boolean isOwner = project.getOwner().getId().equals(currentUser.getId());
            if(isAssignee){
                return;
            }
            if(isOwner){
                return;
            }
        } throw new ForbiddenException("Недостаточно прав");
    }

    @PreAuthorize("isAuthenticated()")
    public List<CommentResponseDto> getComments(Long taskId) {

        UserEntity currentUser = userService.getCurrentUser();
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(()-> new NotFoundException("Task not found"));

        checkCommentPermission(currentUser, task);

        List<CommentEntity> comments = commentRepository.findAllByTaskIdOrderByCreatedAtAsc(taskId);
        return  comments.stream().map(commentMapper::toDto).collect(Collectors.toList());

    }

}
