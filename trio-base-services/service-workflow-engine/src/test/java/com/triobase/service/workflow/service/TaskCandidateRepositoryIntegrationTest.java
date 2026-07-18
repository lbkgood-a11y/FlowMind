package com.triobase.service.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.service.workflow.entity.Task;
import com.triobase.service.workflow.entity.TaskOperation;
import com.triobase.service.workflow.mapper.TaskMapper;
import com.triobase.service.workflow.mapper.TaskOperationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.temporal.test-server.enabled=true",
                "spring.temporal.start-workers=false"
        })
class TaskCandidateRepositoryIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse(System.getProperty("test.postgres.image", "postgres:15-alpine"))
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("triobase_test")
            .withUsername("triobase")
            .withPassword("triobase");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskOperationMapper taskOperationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareTask() {
        jdbcTemplate.update("DELETE FROM wf_task_operation");
        jdbcTemplate.update("DELETE FROM wf_task_candidate");
        jdbcTemplate.update("DELETE FROM wf_participant_resolution");
        jdbcTemplate.update("DELETE FROM wf_task");
        jdbcTemplate.update("DELETE FROM wf_node_record");
        jdbcTemplate.update("DELETE FROM wf_process_instance");
        jdbcTemplate.update("DELETE FROM wf_process_package");

        jdbcTemplate.update("""
                INSERT INTO wf_process_package
                    (id, process_key, name, category, version, status, process_json)
                VALUES (?, ?, ?, 'approval', 1, 'PUBLISHED', ?)
                """, "package-1", "expense", "Expense", "{\"flow\":{\"nodes\":[]}}");
        jdbcTemplate.update("""
                INSERT INTO wf_process_instance
                    (id, process_package_id, process_key, process_name, version, status,
                     initiator_id, initiator_name)
                VALUES (?, ?, ?, ?, 1, 'RUNNING', ?, ?)
                """, "instance-1", "package-1", "expense", "Expense", "starter", "Starter");
        jdbcTemplate.update("""
                INSERT INTO wf_task
                    (id, process_instance_id, process_key, process_name, node_id, node_name,
                     node_type, title, status, assignee_type)
                VALUES (?, ?, ?, ?, ?, ?, 'APPROVAL', ?, 'PENDING', 'ROLE')
                """, "task-1", "instance-1", "expense", "Expense", "approve-1",
                "Finance approval", "Expense - Finance approval");
        insertCandidate("candidate-1", "user-1", "Alice");
        insertCandidate("candidate-2", "user-2", "Bob");
    }

    @Test
    void candidatesSeeUnclaimedTaskAndNonCandidateDoesNot() {
        IPage<Task> aliceTasks = taskMapper.selectPendingForUser(new Page<>(1, 10), "user-1");
        IPage<Task> outsiderTasks = taskMapper.selectPendingForUser(new Page<>(1, 10), "user-3");

        assertEquals(List.of("task-1"),
                aliceTasks.getRecords().stream().map(Task::getId).toList());
        assertEquals(0, outsiderTasks.getTotal());
        assertEquals(0, taskMapper.claimPendingTask("task-1", "user-3", "Outsider"));
    }

    @Test
    void concurrentCandidatesCanClaimTaskExactlyOnce() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> alice = executor.submit(() -> {
                start.await();
                return taskMapper.claimPendingTask("task-1", "user-1", "Alice");
            });
            Future<Integer> bob = executor.submit(() -> {
                start.await();
                return taskMapper.claimPendingTask("task-1", "user-2", "Bob");
            });

            start.countDown();
            assertEquals(1, alice.get() + bob.get());
        }

        Task claimed = taskMapper.selectById("task-1");
        assertTrue(List.of("user-1", "user-2").contains(claimed.getAssigneeId()));
        String losingUser = "user-1".equals(claimed.getAssigneeId()) ? "user-2" : "user-1";
        assertEquals(0, taskMapper.selectPendingForUser(new Page<>(1, 10), losingUser).getTotal());
    }

    @Test
    void operationIdIsImmutableAndInsertedExactlyOnce() {
        TaskOperation operation = new TaskOperation();
        operation.setId("operation-1");
        operation.setOperationId("approve-request-1");
        operation.setProcessInstanceId("instance-1");
        operation.setSourceTaskId("task-1");
        operation.setAction("APPROVE");
        operation.setOperatorId("user-1");
        operation.setOperatorName("Alice");
        operation.setStatus("ACCEPTED");
        operation.setTraceId("trace-001");

        assertEquals(1, taskOperationMapper.insertIfAbsent(operation));

        TaskOperation duplicate = new TaskOperation();
        duplicate.setId("operation-2");
        duplicate.setOperationId("approve-request-1");
        duplicate.setProcessInstanceId("instance-1");
        duplicate.setSourceTaskId("task-1");
        duplicate.setAction("APPROVE");
        duplicate.setOperatorId("user-1");
        duplicate.setStatus("ACCEPTED");
        assertEquals(0, taskOperationMapper.insertIfAbsent(duplicate));

        TaskOperation persisted = taskOperationMapper.selectOne(null);
        assertEquals("trace-001", persisted.getTraceId());
        assertEquals("task-1", persisted.getSourceTaskId());
    }

    private void insertCandidate(String id, String userId, String username) {
        jdbcTemplate.update("""
                INSERT INTO wf_task_candidate
                    (id, task_id, user_id, username, source_type, source_ref)
                VALUES (?, 'task-1', ?, ?, 'ROLE', 'FINANCE')
                """, id, userId, username);
    }
}
