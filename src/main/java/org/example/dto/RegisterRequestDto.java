package org.example.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequestDto {

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 6, max = 15, message = "Пароль должен содержать от 6 до 15 символов")
    private String password;

    @NotBlank(message = "Имя пользователя обязательна")
    @Size(min = 3, max = 15)
    private String firstname;

    @NotBlank(message = "Фамилия пользователя обязательна")
    @Size(min = 3, max = 15)
    private String lastname;

}
