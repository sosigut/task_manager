package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.cache.CacheInvalidationService;
import org.example.dto.CreateTaskRequestDto;
import org.example.dto.UpdateTaskRequestDto;
import org.example.entity.*;
import org.example.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

@Transactional
@AutoConfigureMockMvc
@ActiveProfiles("test")
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

    @MockBean
    private CacheInvalidationService cacheInvalidationService;

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
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
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
    @Transactional
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
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
    @Transactional
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
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
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
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

    @Test
    @Transactional
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
    void shouldGetTasksWithPagination() throws Exception {

        TaskEntity task1 = TaskEntity.builder()
                .title("Задача 1")
                .description("Описание 1")
                .status(Status.TODO)
                .project(project)
                .assignee(user)
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();

        TaskEntity task2 = TaskEntity.builder()
                .title("Задача 2")
                .description("Описание 2")
                .status(Status.IN_PROGRESS)
                .project(project)
                .assignee(user)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        taskRepository.save(task1);
        taskRepository.save(task2);

        mockMvc.perform(get("/projects/" + project.getId() + "/tasks")
                        .param("limit", "10") // Передаем параметр пагинации
                        .contentType(MediaType.APPLICATION_JSON))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items", hasSize(2)))

                .andExpect(jsonPath("$.items[0].title").exists());
    }

    @Test
    @Transactional
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
    void shouldUpdateTask_whenDataIsValid() throws Exception {

        UpdateTaskRequestDto dto = UpdateTaskRequestDto.builder()
                .title("title1")
                .description("description1")
                .build();

        TaskEntity task1 = TaskEntity.builder()
                .title("Задача 1")
                .description("Описание 1")
                .status(Status.TODO)
                .project(project)
                .assignee(user)
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();
        taskRepository.save(task1);

        String jsonRequest = objectMapper.writeValueAsString(dto);

        mockMvc.perform(patch("/projects/task/" + task1.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("title1"))
                .andExpect(jsonPath("$.description").value("description1"));

    }

    @Test
    @Transactional
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
    void shouldDeleteTask_whenUserIsManager() throws Exception {

        TaskEntity task1 = TaskEntity.builder()
                .title("Задача 1")
                .description("Описание 1")
                .status(Status.TODO)
                .project(project)
                .assignee(user)
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();
        taskRepository.save(task1);

        mockMvc.perform(delete("/projects/task/" + task1.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(taskRepository.findById(task1.getId())).isEmpty();

    }

    @Test
    @Transactional
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
    void shouldReturnNotFound_whenGettingTasksForNonExistentProject() throws Exception {

        mockMvc.perform(get("/projects/99999/tasks")
                .param("limit", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

    }

    @Test
    @Transactional
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
    void shouldReturnForbidden_whenUserReadsTasksFromAnotherTeam() throws Exception {

        UserEntity alienUser = UserEntity.builder()
                .publicUid("alien123")
                .email("alien@mail.ru")
                .password("password")
                .firstName("alien")
                .lastName("alien")
                .role(Role.MANAGER)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(alienUser);

        TeamEntity alienTeam = TeamEntity.builder()
                .name("Alien Team")
                .createdBy(alienUser)
                .createdAt(LocalDateTime.now())
                .build();
        teamRepository.save(alienTeam);

        ProjectEntity alienProject = ProjectEntity.builder()
                .name("Alien Project")
                .description("Alien Project")
                .owner(alienUser)
                .team(alienTeam)
                .createdAt(LocalDateTime.now())
                .build();
        projectRepository.save(alienProject);

        mockMvc.perform(get("/projects/" + alienProject.getId() + "/tasks")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

    }

}
