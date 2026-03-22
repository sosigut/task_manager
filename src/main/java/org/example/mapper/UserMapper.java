package org.example.mapper;

import org.example.dto.PublicUidSearchResposeDto;
import org.example.dto.RegisterRequestDto;
import org.example.dto.UserResponseDto;
import org.example.entity.UserEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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
                .createdAt(LocalDateTime.now())
                .build();
    }

    public PublicUidSearchResposeDto searchToDto(UserEntity user, String publicUid){
        if (user == null) {
            return null;
        }

        return PublicUidSearchResposeDto.builder()
                .id(user.getId())
                .publicUid(publicUid)
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

}
