package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.CreateTaskRequestDto;
import org.example.entity.*;
import org.example.repository.*;
import org.junit.jupiter.api.BeforeEach;
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
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity user;
    private TeamEntity team;
    private ProjectEntity project;
    private TeamMemberEntity membership;

    @BeforeEach
    void setUp() {
        user = UserEntity.builder()
                .publicUid("14223654")
                .email("test@mail.ru")
                .password("testtest")
                .firstName("test")
                .lastName("test")
                .role(Role.MANAGER)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(user);

        team = TeamEntity.builder()
                .name("Test Team")
                .createdBy(user)
                .createdAt(LocalDateTime.now())
                .build();
        teamRepository.save(team);

        project = ProjectEntity.builder()
                .name("Test Project")
                .description("Test Project")
                .owner(user)
                .team(team)
                .createdAt(LocalDateTime.now())
                .build();
        projectRepository.save(project);

        membership = TeamMemberEntity.builder()
                .team(team)
                .user(user)
                .role(TeamRole.MANAGER)
                .joinedAt(LocalDateTime.now())
                .build();
        teamMemberRepository.save(membership);
    }

    @Test
    @Transactional
    @WithMockUser(username = "test@mail.ru")
    void shouldCreateTaskViaApi() throws Exception {

        CreateTaskRequestDto dto = CreateTaskRequestDto.builder()
                .title("title")
                .description("description")
                .assigneeId(user.getId())
                .build();

        String jsonRequest = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/projects/" + project.getId() + "/tasks") // Укажи свой URL контроллера
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
    @WithMockUser(username = "test@mail.ru")
    @Transactional
    void shouldReturnBadRequest_whenTaskTitleIsEmpty() throws Exception{

        CreateTaskRequestDto dto = CreateTaskRequestDto.builder()
                .title("")
                .description("description")
                .assigneeId(user.getId())
                .build();

        String jsonRequest = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/projects/" + project.getId() + "/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))

                // Проверяем, что сервер вернул ошибку 400 Bad Request
                .andExpect(status().isBadRequest());

    }

    @Test
    @Transactional
    void shouldReturnUnauthorized_whenUserIsNotAuthenticated() throws Exception{

        CreateTaskRequestDto dto = CreateTaskRequestDto.builder()
                .title("title")
                .description("description")
                .assigneeId(user.getId())
                .build();

        String jsonRequest = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/projects/" + project.getId() + "/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonRequest))
                .andExpect(status().isUnauthorized());

    }

    @Test
    @WithMockUser(username = "test@mail.ru")
    @Transactional
    void shouldReturnForbidden_whenUserHasNoRights() throws Exception {

        membership.setRole(TeamRole.MEMBER);

        CreateTaskRequestDto dto = CreateTaskRequestDto.builder()
                .title("title")
                .description("description")
                .assigneeId(user.getId())
                .build();

        String jsonRequest = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/projects/" + project.getId() + "/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonRequest))
                .andExpect(status().isForbidden());

    }

    @Test
    @Transactional
    @WithMockUser(username = "test@mail.ru")
    void shouldReturnNotFound_whenProjectDoesNotExist() throws Exception{

        CreateTaskRequestDto dto = CreateTaskRequestDto.builder()
                .title("title")
                .description("description")
                .assigneeId(user.getId())
                .build();

        String jsonRequest = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/projects/99999/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isNotFound());

    }
}
