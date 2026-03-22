package org.example.service;

import org.example.dto.PublicUidSearchResposeDto;
import org.example.dto.UserResponseDto;
import org.example.entity.UserEntity;
import org.example.exception.ForbiddenException;
import org.example.mapper.UserMapper;
import org.example.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserMapper mapper;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserMapper mapper, UserRepository userRepository, UserMapper userMapper) {
        this.mapper = mapper;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

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
        return mapper.toDto(user);
    }

    public PublicUidSearchResposeDto findUserByPublicUid(String publicUid){

        if(publicUid != null){

            String trimmedUpperCasePublicUid = publicUid.toUpperCase().trim();
            Optional<UserEntity> findUser = userRepository.findByPublicUid(trimmedUpperCasePublicUid);

            if(findUser.isPresent()){
                UserEntity user = findUser.get();
                return userMapper.searchToDto(user, publicUid);
            } else {
                throw new ForbiddenException("User not found");
            }
        } else  {
            throw new ForbiddenException("PublicUid is null");
        }
    }
}
