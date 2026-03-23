package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.entity.InvitationStatus;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamInvitationResponseDto {

    private Long id;

    private String teamName;

    private Long teamId;

    private Long invitedUserId;

    private Long invitedByUserId;

    private InvitationStatus status;

    private LocalDateTime createdAt;

}
