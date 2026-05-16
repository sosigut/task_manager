package org.example.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTaskRequestDto {

    @Size(min=0, max=200)
    String title;

    @Size(min=0, max=5000)
    String description;

    Long assigneeId;

}
