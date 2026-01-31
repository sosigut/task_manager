package org.example.security.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.example.dto.ErrorResponseDto;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

//401
@Component
@AllArgsConstructor
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException ex) throws IOException, ServletException {

        response.setStatus(401);
        response.setContentType("application/json");

        ErrorResponseDto error = ErrorResponseDto.builder()
                .timestamp(LocalDateTime.now())
                .status(401)
                .error("Unauthorized")
                .message("Authorization please : " + ex)
                .path(request.getRequestURI())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(error));

    }
}
