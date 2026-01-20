package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequestDto {

    @NotNull
    @NotBlank(message = "Название проекта обязательно")
    private String name;

    @NotNull
    @NotBlank(message = "Описание проекта обязательно")
    @Size(max = 1000)
    private String description;



}
