package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentResponseDto {

    private Long id;

    private String text;

    private Long taskId;

    private Long authorId;

    private LocalDateTime createdAt;

}
