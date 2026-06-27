package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.entity.Status;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoveTaskRequestDto {

    @NotBlank(message = "Статус не должен быть пустым")
    @Size(max = 50)
    private Status newStatus;

    private Long prevTaskId;

    private Long nextTaskId;

}
