package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.entity.TeamEntity;
import org.example.entity.UserEntity;
import org.example.exception.NotFoundException;
import org.example.repository.TeamRepository;
import org.example.service.LiveKitService;
import org.example.service.TeamAccessService;
import org.example.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/calls")
@RequiredArgsConstructor
public class LiveKitController {

    private final LiveKitService liveKitService;
    private final UserService userService;
    private final TeamRepository teamRepository;
    private final TeamAccessService teamAccessService;

    @GetMapping("/team/{teamId}/token")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> getCallToken(@PathVariable Long teamId) {

        UserEntity currentUser = userService.getCurrentUser();

        TeamEntity team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));

        teamAccessService.checkMembership(team, currentUser);

        String roomName = "team_" + team.getId();

        String identity = "user_" + currentUser.getId();

        String token = liveKitService.createToken(roomName, identity, currentUser.getFirstName());

        return ResponseEntity.ok(Map.of("token", token));
    }
}
