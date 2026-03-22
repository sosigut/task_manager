package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamResponseDto {

    private Long id;

    private String teamName;

    private LocalDateTime createdAt;

    private Long createdByUserId;

    private String createdByUserUid;
}
