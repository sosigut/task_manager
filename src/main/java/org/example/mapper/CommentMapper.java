package org.example.mapper;

import org.example.dto.CommentResponseDto;
import org.example.dto.CreateCommentRequestDto;
import org.example.entity.CommentEntity;
import org.example.entity.TaskEntity;
import org.example.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CommentMapper {

    public CommentEntity toEntity
            (CreateCommentRequestDto dto,
             TaskEntity task,
             UserEntity author){

        if(dto == null) return null;

        return CommentEntity.builder()
                .text(dto.getText().trim())
                .author(author)
                .task(task)
                .createdAt(LocalDateTime.now())
                .build();

    }

    public CommentResponseDto toDto(CommentEntity entity){

        if(entity == null) return null;

        return CommentResponseDto.builder()
                .id(entity.getId())
                .text(entity.getText())
                .taskId(entity.getTask().getId())
                .authorId(entity.getAuthor().getId())
                .createdAt(entity.getCreatedAt())
                .build();

    }

}
