package org.example.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.example.dto.CreateTeamRequestDto;
import org.example.dto.TeamMemberResponseDto;
import org.example.dto.TeamResponseDto;
import org.example.service.TeamService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/teams")
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public TeamResponseDto createTeam(@Valid @RequestBody CreateTeamRequestDto dto) {
        return teamService.createTeam(dto);
    }

    @GetMapping("/my")
    public List<TeamResponseDto> getMyTeams() {
        return teamService.getMyTeams();
    }

    @GetMapping("/{teamId}/members")
    public List<TeamMemberResponseDto> getTeamMembers(@PathVariable Long teamId) {
        return teamService.getTeamMembers(teamId);
    }

}
