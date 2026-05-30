package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.config.cache.CacheInvalidationService;
import org.example.dto.CreateProjectRequestDto;
import org.example.dto.UpdateProjectRequestDto;
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

import java.time.LocalDateTime;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

@Transactional
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ProjectControllerIT extends IntegrationTestBase{

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
    private TeamMemberEntity membership;
    private ProjectEntity project;

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

        membership = TeamMemberEntity.builder()
                .team(team)
                .user(user)
                .role(TeamRole.MANAGER)
                .joinedAt(LocalDateTime.now())
                .build();
        teamMemberRepository.save(membership);

        project = ProjectEntity.builder()
                .name("Test Project")
                .description("Test Project")
                .owner(user)
                .team(team)
                .createdAt(LocalDateTime.now())
                .build();
        projectRepository.save(project);


    }

    @Test
    @Transactional
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
    void shouldCreateProjectViaApi() throws Exception {


        CreateProjectRequestDto dto = CreateProjectRequestDto.builder()
                .name("Test Project")
                .description("Test Project")
                .teamId(team.getId())
                .build();

        String jsonRequest = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/projects/team/{teamId}",team.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Project"))
                .andExpect(jsonPath("$.description").value("Test Project"));

    }

    @Test
    @Transactional
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
    void shouldGetAllUserProjects() throws Exception {

        ProjectEntity project1 = ProjectEntity.builder()
                .name("Test Project")
                .description("Test Project")
                .owner(user)
                .team(team)
                .createdAt(LocalDateTime.now())
                .build();
        projectRepository.save(project1);

        ProjectEntity project2 = ProjectEntity.builder()
                .name("Test Project")
                .description("Test Project")
                .owner(user)
                .team(team)
                .createdAt(LocalDateTime.now())
                .build();
        projectRepository.save(project2);

        mockMvc.perform(get("/projects/my")
                    .contentType(MediaType.APPLICATION_JSON)
                    .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items", hasSize(3)))

                .andExpect(jsonPath("$.items[0].name").exists());

    }

    @Test
    @Transactional
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
    void shouldUpdateProject() throws Exception {

        UpdateProjectRequestDto dto = UpdateProjectRequestDto.builder()
                .name("Test Project1")
                .description("Test Project1")
                .build();

        String jsonRequest = objectMapper.writeValueAsString(dto);

        mockMvc.perform(patch("/projects/{projectId}",project.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Project1"))
                .andExpect(jsonPath("$.description").value("Test Project1"));

    }

    @Test
    @Transactional
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
    void shouldDeleteProject() throws Exception {

        mockMvc.perform(delete("/projects/{projectId}", project.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(projectRepository.findById(project.getId())).isEmpty();

    }

    @Test
    @Transactional
    @WithUserDetails(
            value = "test@mail.ru",
            setupBefore = org.springframework.security.test.context.support.TestExecutionEvent.TEST_EXECUTION
    )
    void shouldReturnForbidden_whenCreatingProjectInAlienTeam() throws Exception {

        UserEntity alienUser = UserEntity.builder()
                .publicUid("14222654")
                .email("test1@mail.ru")
                .password("testtest")
                .firstName("test")
                .lastName("test")
                .role(Role.MANAGER)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(alienUser);

        TeamEntity team1 = TeamEntity.builder()
                .name("Test Team")
                .createdBy(alienUser)
                .createdAt(LocalDateTime.now())
                .build();
        teamRepository.save(team1);

        CreateProjectRequestDto dto = CreateProjectRequestDto.builder()
                .name("Test Project")
                .description("Test Project")
                .teamId(team1.getId())
                .build();

        String jsonRequest = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/projects/team/{teamId}",team1.getId())
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
    void shouldReturnBadRequest_whenProjectNameIsEmpty() throws Exception {

        CreateProjectRequestDto dto = CreateProjectRequestDto.builder()
                .name("")
                .description("Test Project")
                .teamId(team.getId())
                .build();

        String jsonRequest = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/projects/team/{teamId}",team.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isBadRequest());

    }

}
