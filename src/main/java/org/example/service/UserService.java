package org.example.service;

import lombok.AllArgsConstructor;
import org.example.dto.PublicUidSearchResponseDto;
import org.example.dto.UserResponseDto;
import org.example.entity.UserEntity;
import org.example.exception.NotFoundException;
import org.example.mapper.UserMapper;
import org.example.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserEntity getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Пользователь не аутентифицирован");
        }
        Object principal = authentication.getPrincipal();
        if(principal instanceof String && principal.equals("anonymousUser")){
            throw new AuthenticationCredentialsNotFoundException("Пользователь не аутентифицирован");
        }
        if (principal instanceof UserEntity) {
            return (UserEntity) principal;
        } else {
            throw new BadCredentialsException("Неверный тип principal");
        }
    }

    @PreAuthorize("isAuthenticated()")
    public UserResponseDto getMe() {
        UserEntity user = getCurrentUser();
        return userMapper.toDto(user);
    }

    public PublicUidSearchResponseDto findUserByPublicUid(String publicUid){

        if(Objects.equals(publicUid, " ")) {
            throw new IllegalArgumentException("PublicUid is empty");
        }

        if(publicUid != null){

            String trimmedUpperCasePublicUid = publicUid.toUpperCase().trim();
            Optional<UserEntity> findUser = userRepository.findByPublicUid(trimmedUpperCasePublicUid);

            if(findUser.isPresent()){
                UserEntity user = findUser.get();
                return userMapper.searchToDto(user);
            } else {
                throw new NotFoundException("User not found");
            }
        } else  {
            throw new IllegalArgumentException("PublicUid is null");
        }
    }
}
