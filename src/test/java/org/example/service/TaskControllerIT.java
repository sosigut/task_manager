package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.CreateTaskRequestDto;
import org.example.entity.*;
import org.example.repository.ProjectRepository;
import org.example.repository.TaskRepository;
import org.example.repository.TeamRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDateTime;

@Transactional
@AutoConfigureMockMvc
public class TaskControllerIT extends IntegrationTestBase{

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "test@mail.ru")
    void shouldCreateTaskViaApi() throws Exception {

        UserEntity user = UserEntity.builder()
                .publicUid("14223654")
                .email("test@mail.ru")
                .password("testtest")
                .firstName("test")
                .lastName("test")
                .role(Role.MANAGER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .name("Test Team")
                .createdBy(user)
                .createdAt(LocalDateTime.now())
                .build();

        ProjectEntity project = ProjectEntity.builder()
                .name("Test Project")
                .description("Test Project")
                .owner(user)
                .team(team)
                .createdAt(LocalDateTime.now())
                .build();

        CreateTaskRequestDto dto = CreateTaskRequestDto.builder()
                .title("title")
                .description("description")
                .assigneeId(user.getId())
                .build();

        userRepository.save(user);
        teamRepository.save(team);
        projectRepository.save(project);

        String jsonRequest = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("projects/" + project.getId() + "/tasks") // Укажи свой URL контроллера
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))

                // Проверяем, что HTTP-статус = 200 OK (или 201 Created, смотря как у тебя в контроллере)
                .andExpect(status().isOk())

                // Проверяем, что в вернувшемся JSON поле title совпадает с нашим
                .andExpect(jsonPath("$.title").value("title"))

                // Можем проверить и другие поля
                .andExpect(jsonPath("$.assigneeId").value(user.getId()));

    }

    @Test
    @Transactional
    void shouldReturnBadRequest_whenTaskTitleIsEmpty() throws Exception{

        UserEntity user = UserEntity.builder()
                .publicUid("14223654")
                .email("test@mail.ru")
                .password("testtest")
                .firstName("test")
                .lastName("test")
                .role(Role.MANAGER)
                .createdAt(LocalDateTime.now())
                .build();

        TeamEntity team = TeamEntity.builder()
                .name("Test Team")
                .createdBy(user)
                .createdAt(LocalDateTime.now())
                .build();

        ProjectEntity project = ProjectEntity.builder()
                .name("Test Project")
                .description("Test Project")
                .owner(user)
                .team(team)
                .createdAt(LocalDateTime.now())
                .build();

        CreateTaskRequestDto dto = CreateTaskRequestDto.builder()
                .title("")
                .description("description")
                .assigneeId(user.getId())
                .build();

        String jsonRequest = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/api/tasks/" + project.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))

                // Проверяем, что сервер вернул ошибку 400 Bad Request
                .andExpect(status().isBadRequest());

    }
}
