package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.TeamInvitationRequestDto;
import org.example.dto.TeamInvitationResponseDto;
import org.example.entity.*;
import org.example.exception.ForbiddenException;
import org.example.exception.NotFoundException;
import org.example.mapper.TeamInvitationMapper;
import org.example.repository.TeamInvitationRepository;
import org.example.repository.TeamMemberRepository;
import org.example.repository.TeamRepository;
import org.example.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamInvitationService {

    Set<TeamRole> ALLOWED_ROLES = Set.of(TeamRole.OWNER, TeamRole.MANAGER);

    private final TeamInvitationRepository teamInvitationRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TeamInvitationMapper teamInvitationMapper;

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public TeamInvitationResponseDto inviteUserToTeam(TeamInvitationRequestDto dto){

        if(dto == null){
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        if(dto.getInvitedUserId() == null || dto.getTeamId() == null){
            throw new IllegalArgumentException("Arguments cannot be null");
        }

        UserEntity currentUser = userService.getCurrentUser();

        TeamEntity team = teamRepository.findById(dto.getTeamId())
                .orElseThrow(() -> new NotFoundException("Team not found"));

        TeamMemberEntity membership = teamMemberRepository
                .findByTeamIdAndUserId(team.getId(), currentUser.getId())
                .orElseThrow(() -> new ForbiddenException("The user is not a member of the team"));

        if(!ALLOWED_ROLES.contains(membership.getRole())){
            throw new ForbiddenException("You don't have permission to invite members. Required roles: " + ALLOWED_ROLES);
        }

        UserEntity invitedUser = userRepository.findById(dto.getInvitedUserId())
                .orElseThrow(() -> new NotFoundException("User to invite not found"));
        if (currentUser.getId().equals(invitedUser.getId())) {
            throw new IllegalArgumentException("You cannot invite yourself to the team");
        }

        boolean isTeamMember = teamMemberRepository.existsByTeamIdAndUserId(team.getId(), invitedUser.getId());
        if(isTeamMember){
            throw new IllegalArgumentException("The invited user is already a member of the team");
        }

        checkInvitationSpamProtection(team, invitedUser);

        TeamInvitationEntity invitation = TeamInvitationEntity.builder()
                .team(team)
                .invitedUser(invitedUser)
                .invitedBy(currentUser)
                .status(InvitationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        TeamInvitationEntity save = teamInvitationRepository.save(invitation);

        return teamInvitationMapper.toDto(save);

    }

    private void checkInvitationSpamProtection(TeamEntity team, UserEntity invitedUser){

        final int COOLDOWN_MINUTES = 15;

        Optional<TeamInvitationEntity> lastInvitation = teamInvitationRepository
                .findTopByTeamIdAndInvitedUserIdOrderByCreatedAtDesc(
                        team.getId(), invitedUser.getId()
                );

        if(lastInvitation.isPresent()){
            TeamInvitationEntity lastInvitationEntity = lastInvitation.get();
            LocalDateTime lastInvitationTime = lastInvitationEntity.getCreatedAt();
            LocalDateTime now = LocalDateTime.now();

            long minutesSinceLastInvite = ChronoUnit.MINUTES.between(lastInvitationTime, now);

            if (lastInvitationEntity.getStatus() == InvitationStatus.PENDING) {
                throw new IllegalArgumentException("User already has a pending invitation to this team");
            }

            if (minutesSinceLastInvite < COOLDOWN_MINUTES) {
                throw new IllegalArgumentException(
                        String.format("You can invite this user again after %d minutes. " +
                                        "Last invitation was %d minutes ago (status: %s)",
                                COOLDOWN_MINUTES, minutesSinceLastInvite, lastInvitationEntity.getStatus())
                );
            }
        }
    }

    @PreAuthorize("isAuthenticated()")
    public List<TeamInvitationResponseDto> getMyInvitations(){

        UserEntity currentUser = userService.getCurrentUser();

        List<TeamInvitationEntity> invitations = teamInvitationRepository.findAllByInvitedUserId(currentUser.getId());

        return invitations.stream().map(teamInvitationMapper::toDto).collect(Collectors.toList());
    }

    @PreAuthorize("isAuthenticated()")
    public List<TeamInvitationResponseDto> getMyPendingInvitations(){

        UserEntity currentUser = userService.getCurrentUser();

        List<TeamInvitationEntity> invitations = teamInvitationRepository.findAllByInvitedUserIdAndStatus(currentUser.getId(), InvitationStatus.PENDING);

        return invitations.stream().map(teamInvitationMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public TeamInvitationResponseDto acceptInvitation(Long invitationId){

        if(invitationId == null){
            throw new IllegalArgumentException("Arguments cannot be null");
        }

        UserEntity currentUser = userService.getCurrentUser();

        TeamInvitationEntity invitation = teamInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));

        if(!invitation.getInvitedUser().getId().equals(currentUser.getId())){
            throw new ForbiddenException("You cannot accept this invitation");
        }

        if(!(invitation.getStatus() == InvitationStatus.PENDING)){
            throw new IllegalArgumentException("invitation is not pending");
        }

        boolean isAlreadyMember = teamMemberRepository
                .existsByTeamIdAndUserId(invitation.getTeam().getId(), currentUser.getId());
        if (isAlreadyMember) {
            throw new IllegalArgumentException("User is already a member of the team");
        }

        TeamMemberEntity teamMember = TeamMemberEntity.builder()
                .team(invitation.getTeam())
                .user(currentUser)
                .role(TeamRole.MEMBER)
                .joinedAt(LocalDateTime.now())
                .build();

        teamMemberRepository.save(teamMember);

        invitation.setStatus(InvitationStatus.ACCEPTED);

        TeamInvitationEntity saveInvitation = teamInvitationRepository.save(invitation);

        return teamInvitationMapper.toDto(saveInvitation);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public TeamInvitationResponseDto declineInvitation(Long invitationId){

        if(invitationId == null){
            throw new IllegalArgumentException("Arguments cannot be null");
        }

        UserEntity currentUser = userService.getCurrentUser();

        TeamInvitationEntity invitation = teamInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));

        if (!invitation.getInvitedUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("This invitation does not belong to you");
        }

        if(!(invitation.getStatus() == InvitationStatus.PENDING)){
            throw new IllegalArgumentException("The invitation status is not PENDING");
        }

        invitation.setStatus(InvitationStatus.DECLINED);

        TeamInvitationEntity save = teamInvitationRepository.save(invitation);

        return teamInvitationMapper.toDto(save);

    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public TeamInvitationResponseDto cancelInvitation(Long invitationId){

        if(invitationId == null){
            throw new IllegalArgumentException("Arguments cannot be null");
        }

        UserEntity currentUser = userService.getCurrentUser();

        TeamInvitationEntity invitation = teamInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));

        if(currentUser.getId().equals(invitation.getInvitedBy().getId())) {
            throw new ForbiddenException("You cannot cancel this invitation");
        }

        if(invitation.getStatus() != InvitationStatus.PENDING){
            throw new IllegalArgumentException("invitation is not pending");
        }

        invitation.setStatus(InvitationStatus.CANCELLED);
        TeamInvitationEntity save = teamInvitationRepository.save(invitation);

        return teamInvitationMapper.toDto(save);
    }

}
