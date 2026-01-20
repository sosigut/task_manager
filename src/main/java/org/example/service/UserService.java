package org.example.service;

import org.example.dto.UserResponseDto;
import org.example.entity.UserEntity;
import org.example.mapper.UserMapper;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class UserService {

    private final UserMapper mapper;

    public UserService(UserMapper mapper) {
        this.mapper = mapper;
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


    public UserResponseDto getMe() {
        UserEntity user = getCurrentUser();
        return mapper.toDto(user);
    }
}
