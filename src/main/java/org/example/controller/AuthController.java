package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.LoginRequestDto;
import org.example.dto.LoginResponseDto;
import org.example.dto.RegisterRequestDto;
import org.example.dto.UserResponseDto;
import org.example.service.AuthService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public UserResponseDto register(RegisterRequestDto registerRequestDto) throws Exception {
        return authService.register(registerRequestDto);
    }

    @PostMapping("/login")
    public LoginResponseDto login (LoginRequestDto loginRequestDto) throws Exception {
        return authService.login(loginRequestDto);
    }

}
