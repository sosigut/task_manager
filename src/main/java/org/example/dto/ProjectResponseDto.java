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
public class ProjectResponseDto {

    private Long id;

    private String name;

    private String description;

    private Long ownerId;

    private LocalDateTime createdAt;

}
