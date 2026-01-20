package org.example.mapper;

import org.example.dto.RegisterRequestDto;
import org.example.dto.UserResponseDto;
import org.example.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserEntity toEntity(RegisterRequestDto dto) {
        if (dto == null) {
            return null;
        }

        return UserEntity.builder()
                .email(dto.getEmail().trim())
                .password(dto.getPassword().trim())
                .firstName(dto.getFirstname().trim())
                .lastName(dto.getLastname().trim())
                .build();
    }

    public UserResponseDto toDto(UserEntity user){
        if (user == null) {
            return null;
        }

        return UserResponseDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstname(user.getFirstName())
                .lastname(user.getLastName())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }

}
