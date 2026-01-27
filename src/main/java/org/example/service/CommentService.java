package org.example.service;

import lombok.AllArgsConstructor;
import org.example.dto.CommentResponseDto;
import org.example.dto.CreateCommentRequestDto;
import org.example.entity.CommentEntity;
import org.example.entity.Role;
import org.example.entity.TaskEntity;
import org.example.entity.UserEntity;
import org.example.exception.ForbiddenException;
import org.example.exception.NotFoundException;
import org.example.mapper.CommentMapper;
import org.example.repository.CommentRepository;
import org.example.repository.TaskRepository;
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

    public CommentResponseDto createComment
            (Long taskId, CreateCommentRequestDto dto){

        UserEntity currentUser = userService.getCurrentUser();
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(()-> new NotFoundException("Task not found"));

        CommentEntity comment;

        if(currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER){

            comment = commentMapper.toEntity(dto, task, currentUser);
            CommentEntity saved =  commentRepository.save(comment);
            return commentMapper.toDto(saved);

        }else if(currentUser.getRole() == Role.USER
                && task.getAssignee().getId().equals(currentUser.getId())){

            comment = commentMapper.toEntity(dto, task, currentUser);
            CommentEntity saved =  commentRepository.save(comment);
            return commentMapper.toDto(saved);

        } throw new ForbiddenException("Недостаточно прав доступа");

    }

    public List<CommentResponseDto> getComments(Long taskId) {

        UserEntity currentUser = userService.getCurrentUser();
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(()-> new NotFoundException("Task not found"));

        if(currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MANAGER){
            List<CommentEntity> comments = commentRepository
                    .findAllByTaskIdOrderByCreatedAtAsc(taskId);
            return comments.stream().map(commentMapper::toDto).collect(Collectors.toList());
        } else if(currentUser.getRole() == Role.USER
                && task.getAssignee().getId().equals(currentUser.getId())){
            List<CommentEntity> comments = commentRepository
                    .findAllByTaskIdOrderByCreatedAtAsc(taskId);
            return comments.stream().map(commentMapper::toDto).collect(Collectors.toList());
        } throw new ForbiddenException("Нет прав доступа на просмотр комментариев");

    }

}
