package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.entity.TeamRole;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberResponseDto {

    private Long userId;

    private String publicUid;

    private String firstName;

    private String lastName;

    private TeamRole teamRole;

    private LocalDateTime joinedAt;
}
