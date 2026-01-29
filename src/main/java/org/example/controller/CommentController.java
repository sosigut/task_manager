package org.example.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.dto.CommentResponseDto;
import org.example.dto.CreateCommentRequestDto;
import org.example.service.CommentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public List<CommentResponseDto> getComments(@PathVariable Long taskId) {
        return commentService.getComments(taskId);
    }
}
