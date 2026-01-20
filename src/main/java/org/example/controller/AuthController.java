package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.LoginRequestDto;
import org.example.dto.LoginResponseDto;
import org.example.dto.RegisterRequestDto;
import org.example.dto.UserResponseDto;
import org.example.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public UserResponseDto register(
            @Valid @RequestBody RegisterRequestDto registerRequestDto
    ) throws Exception {
        return authService.register(registerRequestDto);
    }

    @PostMapping("/login")
    public LoginResponseDto login(
            @Valid @RequestBody LoginRequestDto loginRequestDto
    ) throws Exception {
        return authService.login(loginRequestDto);
    }
}
