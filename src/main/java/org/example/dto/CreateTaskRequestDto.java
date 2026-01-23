package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequestDto {

    @NotBlank(message = "Заголовок является обязательным полем")
    private String title;

    @NotBlank(message = "Описание является обязательным полем")
    @Size(max = 1000)
    private String description;

    private Long assigneeId;

}
