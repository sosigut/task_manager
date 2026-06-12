package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private String type;       // Тип события ("STATUS_CHANGED", "NEW_COMMENT", "PROJECT_CREATED")
    private String message;
    private String entityType; // Что именно изменилось ("TASK", "PROJECT", "COMMENT")
    private Long entityId;     // ID того, что изменилось

}
