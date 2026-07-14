package com.triobase.service.workflow.service;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.workflow.client.LowcodeFormClient;
import com.triobase.service.workflow.dto.StartProcessRequest;
import com.triobase.service.workflow.entity.NodeRecord;
import com.triobase.service.workflow.entity.ProcessInstance;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.entity.TaskOperation;
import com.triobase.service.workflow.exception.FormDataValidationException;
import com.triobase.service.workflow.exception.ProcessVersionConflictException;
import com.triobase.service.workflow.mapper.ProcessInstanceMapper;
import com.triobase.service.workflow.mapper.ProcessPackageMapper;
import com.triobase.service.workflow.mapper.NodeRecordMapper;
import com.triobase.service.workflow.mapper.TaskOperationMapper;
import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

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
class ProcessInstanceStartIntegrationTest {

    private static final String PACKAGE_ID = "PKG_RUNTIME_V1";
    private static final String PROCESS_KEY = "runtime_form_test";
    private static final String PROCESS_JSON = """
            {
              "version": "1.0.0",
              "processKey": "runtime_form_test",
              "name": "Runtime Form Test",
              "flow": {
                "nodes": [
                  {"id":"start","type":"START","name":"Start","next":[{"condition":"true","target":"end"}]},
                  {"id":"end","type":"END","name":"End"}
                ]
              }
            }
            """;
    private static final String FORM_SCHEMA = """
            {
              "type":"object",
              "required":["reason","amount"],
              "additionalProperties":false,
              "properties":{
                "reason":{"type":"string","minLength":2},
                "amount":{"type":"number","minimum":0.01}
              }
            }
            """;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
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
    private ProcessInstanceService processInstanceService;

    @Autowired
    private ProcessInstanceMapper processInstanceMapper;

    @Autowired
    private ProcessPackageMapper processPackageMapper;

    @Autowired
    private NodeRecordMapper nodeRecordMapper;

    @Autowired
    private TaskOperationMapper taskOperationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private WorkflowClient workflowClient;

    @MockitoBean
    private LowcodeFormClient lowcodeFormClient;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM wf_task_operation");
        jdbcTemplate.update("DELETE FROM wf_task_candidate");
        jdbcTemplate.update("DELETE FROM wf_participant_resolution");
        jdbcTemplate.update("DELETE FROM wf_task");
        jdbcTemplate.update("DELETE FROM wf_node_record");
        jdbcTemplate.update("DELETE FROM wf_process_instance");
        jdbcTemplate.update("DELETE FROM wf_process_package");
        processPackageMapper.insert(publishedPackage());
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "USER001", "Alice", null, List.of("USER"), List.of(), null, null, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void invalidFormCreatesNeitherInstanceNorWorkflowExecution() {
        StartProcessRequest request = request(PACKAGE_ID, 1);
        request.setFormData(Map.of("amount", "invalid"));

        assertThrows(FormDataValidationException.class,
                () -> processInstanceService.startProcess(request));

        assertEquals(0L, processInstanceMapper.selectCount(null));
        verifyNoInteractions(workflowClient);
    }

    @Test
    void staleVersionCreatesNeitherInstanceNorWorkflowExecution() {
        StartProcessRequest request = request("PKG_STALE", 0);
        request.setFormData(Map.of("reason", "Taxi", "amount", 88.5));

        assertThrows(ProcessVersionConflictException.class,
                () -> processInstanceService.startProcess(request));

        assertEquals(0L, processInstanceMapper.selectCount(null));
        verifyNoInteractions(workflowClient);
    }

    @Test
    void historyReturnsNodeVisitsAndImmutableTaskOperations() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId("INSTANCE_HISTORY");
        instance.setProcessPackageId(PACKAGE_ID);
        instance.setProcessKey(PROCESS_KEY);
        instance.setProcessName("Runtime Form Test");
        instance.setVersion(1);
        instance.setStatus("RUNNING");
        instance.setInitiatorId("USER001");
        instance.setInitiatorName("Alice");
        instance.setStartedAt(LocalDateTime.now());
        processInstanceMapper.insert(instance);

        NodeRecord node = new NodeRecord();
        node.setId("NODE_HISTORY");
        node.setProcessInstanceId(instance.getId());
        node.setNodeId("approve");
        node.setNodeName("Approval");
        node.setNodeType("APPROVAL");
        node.setVisitNo(1);
        node.setStatus("COMPLETED");
        node.setEnteredAt(LocalDateTime.now());
        node.setExitedAt(LocalDateTime.now());
        nodeRecordMapper.insert(node);

        TaskOperation operation = new TaskOperation();
        operation.setId("OP_HISTORY");
        operation.setOperationId("operation-history");
        operation.setProcessInstanceId(instance.getId());
        operation.setAction("APPROVE");
        operation.setOperatorId("USER001");
        operation.setOperatorName("Alice");
        operation.setStatus("ACCEPTED");
        operation.setTraceId("trace-history");
        taskOperationMapper.insert(operation);

        var history = processInstanceService.getHistory(instance.getId());

        assertEquals(1, history.getNodes().size());
        assertEquals("approve", history.getNodes().getFirst().getNodeId());
        assertEquals(1, history.getOperations().size());
        assertEquals("trace-history", history.getOperations().getFirst().getTraceId());
    }

    private StartProcessRequest request(String packageId, int version) {
        StartProcessRequest request = new StartProcessRequest();
        request.setProcessKey(PROCESS_KEY);
        request.setProcessPackageId(packageId);
        request.setVersion(version);
        request.setTitle("Runtime validation");
        return request;
    }

    private ProcessPackage publishedPackage() {
        ProcessPackage pkg = new ProcessPackage();
        pkg.setId(PACKAGE_ID);
        pkg.setProcessKey(PROCESS_KEY);
        pkg.setName("Runtime Form Test");
        pkg.setCategory("approval");
        pkg.setVersion(1);
        pkg.setStatus("PUBLISHED");
        pkg.setProcessJson(PROCESS_JSON);
        pkg.setFormSchema(FORM_SCHEMA);
        pkg.setFormUiSchema("{}");
        return pkg;
    }
}
