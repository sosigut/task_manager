package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.TeamInvitationRequestDto;
import org.example.dto.TeamInvitationResponseDto;
import org.example.service.TeamInvitationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/invitations")
@RequiredArgsConstructor
public class TeamInvitationController {

    private final TeamInvitationService teamInvitationService;

    @PostMapping
    public TeamInvitationResponseDto invite(
            @RequestBody @Valid TeamInvitationRequestDto dto
    ) {
        return teamInvitationService.inviteUserToTeam(dto);
    }

    @GetMapping
    public List<TeamInvitationResponseDto> getMyInvitations() {
        return teamInvitationService.getMyInvitations();
    }

    @GetMapping("/pending")
    public List<TeamInvitationResponseDto> getPending() {
        return teamInvitationService.getMyPendingInvitations();
    }

    @PostMapping("/{id}/accept")
    public TeamInvitationResponseDto accept(@PathVariable Long id) {
        return teamInvitationService.acceptInvitation(id);
    }

    @PostMapping("/{id}/decline")
    public TeamInvitationResponseDto decline(@PathVariable Long id) {
        return teamInvitationService.declineInvitation(id);
    }

    @PostMapping("{id}/cancel")
    public TeamInvitationResponseDto cancel(@PathVariable Long id) {
        return teamInvitationService.cancelInvitation(id);
    }
}
