package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTeamRequestDto {
    @NotBlank(message = "Название команды не должно быть пустым")
    @Size(max = 100)
    private String teamName;
}
