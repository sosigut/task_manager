package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeysetCommentPageResponseDto {

    private List<CommentResponseDto> items;

    private Integer limit;

    private LocalDateTime cursorCreatedAt;
    private Long cursorId;

    private boolean hasNext;

    private String sort = "createdAt:desc,id:desc";
}
