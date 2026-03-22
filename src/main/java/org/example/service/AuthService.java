package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.example.config.PasswordEncoderConfig;
import org.example.dto.LoginRequestDto;
import org.example.dto.LoginResponseDto;
import org.example.dto.RegisterRequestDto;
import org.example.dto.UserResponseDto;
import org.example.entity.Role;
import org.example.entity.UserEntity;
import org.example.mapper.UserMapper;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoderConfig passwordEncoderConfig;
    private final UserMapper mapper;
    private final JwtService jwtService;

    public UserResponseDto register (RegisterRequestDto registerRequestDto) throws Exception{
        String email = registerRequestDto.getEmail().toLowerCase().trim();

        String publicUid = RandomStringUtils.randomAlphanumeric(10).toUpperCase(Locale.ROOT);

        if(userRepository.existsByPublicUid(publicUid)){
            publicUid=RandomStringUtils.randomAlphanumeric(10).toUpperCase(Locale.ROOT);
        }

        if (userRepository.existsByEmail(email)) {
            throw new Exception("Email already exists");
        }

        String hashedPassword = passwordEncoderConfig.hashPassword(registerRequestDto.getPassword());

        UserEntity userEntity = UserEntity.builder()
                .publicUid(publicUid)
                .email(email)
                .password(hashedPassword)
                .firstName(registerRequestDto.getFirstname().trim())
                .lastName(registerRequestDto.getLastname().trim())
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(userEntity);

        return mapper.toDto(userEntity);
    }

    public LoginResponseDto login (LoginRequestDto loginRequestDto) throws Exception {

        String email = loginRequestDto.getEmail().toLowerCase().trim();
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    return new Exception("User not found");
                });

        boolean passwordMatches = passwordEncoderConfig.passwordEncoder().matches(
                loginRequestDto.getPassword(), user.getPassword()
        );

        if (!passwordMatches) {
            throw new Exception("Wrong password");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return LoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .id(user.getId())
                .build();
    }

}
