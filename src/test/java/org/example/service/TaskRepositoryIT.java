package org.example.service;

import org.example.entity.*;
import org.example.repository.ProjectRepository;
import org.example.repository.TaskRepository;
import org.example.repository.TeamRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class TaskRepositoryIT extends IntegrationTestBase {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    @Transactional
    void shouldSaveAndFindTask(){

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

        TaskEntity task = TaskEntity.builder()
                .title("title")
                .description("description")
                .status(Status.TODO)
                .project(project)
                .assignee(user)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        teamRepository.save(team);
        projectRepository.save(project);
        TaskEntity savedTasK = taskRepository.save(task);

        TaskEntity findTask = taskRepository.findById(savedTasK.getId()).orElse(null);

        assertNotNull(findTask);
        assertEquals(task.getProject().getId(), findTask.getProject().getId());


    }

}
