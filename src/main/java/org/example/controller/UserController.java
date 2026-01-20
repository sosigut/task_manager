package org.example.controller;

import lombok.AllArgsConstructor;
import org.example.dto.UserResponseDto;
import org.example.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@AllArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponseDto me() {
        return userService.getMe();
    }


}
