package org.example.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.dto.CommentResponseDto;
import org.example.dto.CreateCommentRequestDto;
import org.example.pagination.KeysetPageResponseDto;
import org.example.service.CommentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@AllArgsConstructor
@RequestMapping("/tasks")
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/{taskId}/comments")
    public CommentResponseDto createComment(
            @Valid @RequestBody CreateCommentRequestDto dto,
            @PathVariable Long taskId) {
        return commentService.createComment(taskId, dto);
    }

    @GetMapping("/{taskId}/comments")
    public KeysetPageResponseDto<CommentResponseDto> getComments(
            @PathVariable Long taskId,
            @RequestParam(required=false) Integer limit,
            @RequestParam(required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursorCreatedAt,
            @RequestParam(required=false) Long cursorId) {
        return commentService.getKeysetTaskComments(taskId, limit, cursorCreatedAt, cursorId);
    }

    @DeleteMapping("/comment/{commentId}")
    public void deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
    }
}
