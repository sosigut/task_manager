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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamInvitationService {

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

        TeamRole role = membership.getRole();
        if(role == TeamRole.MEMBER){
            throw new ForbiddenException("Team member can't invite members");
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

        boolean alreadyInvited = teamInvitationRepository.existsByTeamIdAndInvitedUserIdAndStatus(
                team.getId(), dto.getInvitedUserId(), InvitationStatus.PENDING
        );
        if (alreadyInvited) {
            throw new IllegalArgumentException("User already has a pending invitation to this team");
        }

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

    public List<TeamInvitationResponseDto> getMyInvitations(){

        UserEntity currentUser = userService.getCurrentUser();

        List<TeamInvitationEntity> invitations = teamInvitationRepository.findAllByInvitedUserId(currentUser.getId());

        return invitations.stream().map(teamInvitationMapper::toDto).collect(Collectors.toList());
    }

    public List<TeamInvitationResponseDto> getMyPendingInvitations(){

        UserEntity currentUser = userService.getCurrentUser();

        List<TeamInvitationEntity> invitations = teamInvitationRepository.findAllByInvitedUserIdAndStatus(currentUser.getId(), InvitationStatus.PENDING);

        return invitations.stream().map(teamInvitationMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
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
            throw new IllegalArgumentException("The invitation has been invited");
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

        TeamMemberEntity save = teamMemberRepository.save(teamMember);

        invitation.setStatus(InvitationStatus.ACCEPTED);

        TeamInvitationEntity saveInvitation = teamInvitationRepository.save(invitation);

        return teamInvitationMapper.toDto(saveInvitation);
    }

    @Transactional
    public TeamInvitationResponseDto declineInvitation(Long invitationId){

        if(invitationId == null){
            throw new IllegalArgumentException("Arguments cannot be null");
        }

        UserEntity currentUser = userService.getCurrentUser();

        TeamInvitationEntity invitation = teamInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));

        if (!invitation.getInvitedUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("The invitation has been invited");
        }

        if(!(invitation.getStatus() == InvitationStatus.PENDING)){
            throw new IllegalArgumentException("The invitation status is not PENDING");
        }

        invitation.setStatus(InvitationStatus.DECLINED);

        TeamInvitationEntity save = teamInvitationRepository.save(invitation);

        return teamInvitationMapper.toDto(save);

    }

}
